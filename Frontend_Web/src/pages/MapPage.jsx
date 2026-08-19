import React, { useMemo, useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useTrip } from '../context/TripContext';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import 'leaflet.heat'; // 🟢 Import Leaflet Heat plugin
import { mapApi } from '../services/api';

// Fix default Leaflet icon paths
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-shadow.png',
});

// Component to handle map view updates
const RecenterMap = ({ center, zoom }) => {
  const map = useMap();
  useEffect(() => {
    if (center) {
      map.setView(center, zoom || map.getZoom());
    }
  }, [center, zoom, map]);
  return null;
};

RecenterMap.propTypes = {
  center: PropTypes.arrayOf(PropTypes.number),
  zoom: PropTypes.number,
};

// Component to render the Heatmap around a specific point
const HeatmapLayer = ({ center, level, visible }) => {
  const map = useMap();
  const heatLayerRef = React.useRef(null);

  useEffect(() => {
    if (heatLayerRef.current) {
      map.removeLayer(heatLayerRef.current);
      heatLayerRef.current = null;
    }

    if (!visible || !center || !level) return;

    // Generate random points around the center to simulate a real heatmap blob
    const points = [];
    const intensity = level.toUpperCase() === 'HIGH' ? 0.8 : (level.toUpperCase() === 'MODERATE' ? 0.5 : 0.3);
    const count = level.toUpperCase() === 'HIGH' ? 100 : 50;
    const spread = 0.008; // How far the heatmap spreads

    for (let i = 0; i < count; i++) {
      const latOffset = (Math.random() - 0.5) * spread;
      const lngOffset = (Math.random() - 0.5) * spread;
      points.push([center[0] + latOffset, center[1] + lngOffset, intensity]);
    }

    // Add center point with high intensity
    points.push([center[0], center[1], intensity * 1.5]);

    heatLayerRef.current = L.heatLayer(points, {
      radius: 25,
      blur: 15,
      maxZoom: 17,
      gradient: { 0.4: 'blue', 0.6: 'lime', 0.8: 'yellow', 1: 'red' }
    }).addTo(map);

    return () => {
      if (heatLayerRef.current) {
        map.removeLayer(heatLayerRef.current);
      }
    };
  }, [center, level, visible, map]);

  return null;
};

HeatmapLayer.propTypes = {
  center: PropTypes.arrayOf(PropTypes.number),
  level: PropTypes.string,
  visible: PropTypes.bool,
};

// Custom Marker Icon with Number
const createCustomIcon = (number) => {
  return new L.DivIcon({
    html: `<div class="custom-map-marker">${number}</div>`,
    className: 'custom-div-icon',
    iconSize: [32, 44],
    iconAnchor: [16, 44],
    popupAnchor: [0, -46], // 🟢 Position the popup above the marker tip
  });
};

// 🟢 Custom Icon for Nearby Recommendations (using DivIcon for consistency and to avoid CSP issues)
const recommendationIcon = new L.DivIcon({
  html: '<div class="custom-map-marker recommendation">✨</div>',
  className: 'custom-div-icon',
  iconSize: [32, 44],
  iconAnchor: [16, 44],
  popupAnchor: [0, -46],
});

// Component to handle map clicks
const MapClickHandler = ({ onMapClick }) => {
  useMapEvents({
    click: (e) => {
      onMapClick(e.latlng);
    },
  });
  return null;
};

MapClickHandler.propTypes = {
  onMapClick: PropTypes.func.isRequired,
};

const MapPage = () => {
  const { trips, activeTripId, setActiveTripId, getActiveTrip } = useTrip();
  const trip = getActiveTrip();

  const [selectedDay, setSelectedDay] = useState(1);
  const [mapConfig, setMapConfig] = useState({
    tileUrl: 'https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png', // 🟢 Fresh Mint Style (Alidade Smooth)
    center: [1.3521, 103.8198], // Default to Singapore
    zoom: 12
  });
  const [manualCenter, setManualCenter] = useState(null);
  const [nearbyAttractions, setNearbyAttractions] = useState([]);
  const [crowdData, setCrowdData] = useState(null);
  const [showHeatmap, setShowHeatmap] = useState(false);
  const [routeData, setRouteData] = useState({
    totalLocations: 0,
    estimatedDistanceKm: 0,
    travelTimeMinutes: 0,
    googleMapsUrl: null,
    locations: []
  });
  const [loading, setLoading] = useState(false);

  const dayLocations = useMemo(() => {
    return (trip?.locations || [])
      .filter((loc) => loc.day === selectedDay)
      .map((loc) => ({
        id: loc.id,
        name: loc.name,
        time: loc.time,
        activityType: loc.activityType || 'Visit',
        latitude: loc.lat,
        longitude: loc.lng,
      }));
  }, [trip, selectedDay]);

  // Fetch initial map config — backend fields are tileUrlTemplate / defaultLatitude / ...
  useEffect(() => {
    mapApi.getConfig()
      .then((res) => {
        const cfg = res.data || {};
        const lat = Number(cfg.defaultLatitude);
        const lng = Number(cfg.defaultLongitude);
        setMapConfig({
          tileUrl: cfg.tileUrlTemplate || 'https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png',
          center: Number.isFinite(lat) && Number.isFinite(lng) ? [lat, lng] : [1.3521, 103.8198],
          zoom: cfg.defaultZoom || 12,
        });
      })
      .catch((err) => console.error('Failed to fetch map config', err));
  }, []);

  // Reset selected day when trip changes
  useEffect(() => {
    setSelectedDay(1);
    setManualCenter(null);
  }, [activeTripId]);

  // Reset manual focus when day changes
  useEffect(() => {
    setManualCenter(null);
  }, [selectedDay]);

  // Stats come from GET /trips/{id}/route; stop list/coords come from GET /trips (TripContext).
  useEffect(() => {
    if (!activeTripId) {
      setRouteData({
        totalLocations: dayLocations.length,
        estimatedDistanceKm: 0,
        travelTimeMinutes: 0,
        locations: dayLocations,
      });
      return;
    }
    setLoading(true);
    mapApi.getRoute(activeTripId, selectedDay)
      .then((res) => {
        const data = res.data || {};
        setRouteData({
          totalLocations: data.stopCount ?? dayLocations.length,
          estimatedDistanceKm: Number(data.totalDistanceKm) || 0,
          travelTimeMinutes: Number(data.totalDurationMinutes) || 0,
          googleMapsUrl: data.googleMapsUrl,
          locations: dayLocations,
        });
      })
      .catch((err) => {
        console.error('Failed to fetch route data', err);
        setRouteData({
          totalLocations: dayLocations.length,
          estimatedDistanceKm: 0,
          travelTimeMinutes: 0,
          locations: dayLocations,
        });
      })
      .finally(() => setLoading(false));
  }, [activeTripId, selectedDay, dayLocations]);

  // Get active trips for the selector
  const activeTrips = useMemo(() => {
    return trips.filter(t => t.status === 'ACTIVE');
  }, [trips]);

  const polylinePositions = useMemo(() => {
    return routeData.locations
      .filter(loc => loc.latitude && loc.longitude)
      .map(loc => [loc.latitude, loc.longitude]);
  }, [routeData.locations]);

  const mapCenter = useMemo(() => {
    if (polylinePositions.length > 0) {
      return polylinePositions[0];
    }
    return mapConfig.center || [1.3521, 103.8198];
  }, [polylinePositions, mapConfig.center]);

  const daysArray = Array.from({ length: trip?.dayCount || 1 }, (_, i) => i + 1);

  const handleSyncNavigation = () => {
    if (routeData.googleMapsUrl) {
      window.open(routeData.googleMapsUrl, '_blank');
    } else {
      alert('Navigation URL not available for this route.');
    }
  };

  const handleMapClick = (latlng) => {
    mapApi.getNearby(latlng.lat, latlng.lng)
      .then(res => {
        if (res.data && res.data.items) {
          setNearbyAttractions(res.data.items);
        }
      })
      .catch(err => console.error('Failed to fetch nearby attractions', err));
  };

  const toggleHeatmap = () => {
    const nextState = !showHeatmap;
    setShowHeatmap(nextState);
    if (nextState && !crowdData) {
      mapApi.getCrowdHint(trip?.date)
        .then(res => setCrowdData(res.data))
        .catch(err => console.error('Failed to fetch crowd data', err));
    }
  };

  return (
    <div className="map-page">
      <header className="page-header map-header-layout" style={{marginBottom: '32px'}}>
        <div className="header-text">
          <h1>Explore Map</h1>
          <p>Visualize your itinerary routes and surrounding recommendations.</p>
        </div>

        {/* Trip Selector List */}
        <div className="trip-selector-container">
          <div className="trip-selector-scroll">
            {activeTrips.map(t => (
              <button
                key={t.id}
                className={`trip-select-btn ${activeTripId === t.id ? 'active' : ''}`}
                onClick={() => setActiveTripId(t.id)}
              >
                <div className="trip-dot" style={{ background: 'var(--jade)' }}>
                  {t.shortName}
                </div>
                <span className="trip-name-text">{t.title}</span>
              </button>
            ))}
          </div>
        </div>
      </header>

      <div className="map-container">
        {/* LEFT DAY SELECTOR */}
        <div className="map-day-selector">
          {daysArray.map(dayNum => (
            <button
              key={dayNum}
              className={`map-day-btn ${selectedDay === dayNum ? 'active' : ''}`}
              onClick={() => setSelectedDay(dayNum)}
            >
              <span className="num">{dayNum}</span>
              <span className="label">Day</span>
            </button>
          ))}
        </div>

        <div className="map-view" style={{ position: 'relative' }}>
          {/* MAP OVERLAY CONTROLS */}
          <div style={{
            position: 'absolute', top: '20px', left: '50%', transform: 'translateX(-50%)',
            zIndex: 1000, display: 'flex', gap: '10px'
          }}>
            <button
              className={`btn-shadow-style ${showHeatmap ? 'active' : ''}`}
              onClick={toggleHeatmap}
              style={{
                background: showHeatmap ? 'var(--jade)' : '#fff',
                color: showHeatmap ? '#fff' : 'var(--jade-deep)',
                borderRadius: '99px',
                padding: '8px 24px',
                border: 'none',
                boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
            >
              🔥 {showHeatmap ? 'Disable Heatmap' : 'Show Traffic Heatmap'}
            </button>
          </div>

          {/* CROWD INFO BOX */}
          {showHeatmap && crowdData && (
            <div style={{
              position: 'absolute', bottom: '20px', left: '20px', zIndex: 1000,
              background: 'rgba(255, 255, 255, 0.9)', padding: '16px', borderRadius: '16px',
              border: '1px solid var(--jade)', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxWidth: '240px'
            }}>
              <h4 style={{ margin: '0 0 8px', fontSize: '14px' }}>📈 Seasonal Crowd Level</h4>
              <div style={{
                display: 'inline-block', padding: '4px 10px', borderRadius: '8px',
                background: crowdData.level === 'HIGH' ? 'var(--coral)' : 'var(--mint)',
                color: crowdData.level === 'HIGH' ? '#fff' : 'var(--jade-deep)',
                fontWeight: 'bold', marginBottom: '8px', fontSize: '12px'
              }}>
                {crowdData.level}
              </div>
              <p style={{ margin: 0, fontSize: '12px', color: 'var(--muted)', lineHeight: '1.4' }}>
                {crowdData.note}
              </p>
            </div>
          )}

          <MapContainer
            center={mapCenter}
            zoom={mapConfig.zoom}
            style={{ height: '100%', width: '100%', borderRadius: '24px' }}
          >
            <TileLayer url={mapConfig.tileUrl} />

            <HeatmapLayer
              center={manualCenter || (dayLocations.length > 0 ? [dayLocations[0].latitude, dayLocations[0].longitude] : null)}
              level={crowdData?.level}
              visible={showHeatmap}
            />

            <MapClickHandler onMapClick={handleMapClick} />

            <RecenterMap center={manualCenter || mapCenter} zoom={manualCenter ? 15 : mapConfig.zoom} />

            {polylinePositions.length > 1 && (
              <Polyline
                positions={polylinePositions}
                color="#0E9E8E"
                dashArray="10, 5"
                weight={4}
              />
            )}

            {routeData.locations.map((loc, idx) => (
              loc.latitude && loc.longitude && (
                <Marker
                  key={loc.id || idx}
                  position={[loc.latitude, loc.longitude]}
                  icon={createCustomIcon(idx + 1)}
                >
                  <Popup>
                    <div style={{ fontWeight: 'bold' }}>{loc.name}</div>
                    <div>{loc.time} | {loc.activityType}</div>
                  </Popup>
                </Marker>
              )
            ))}

            {/* NEARBY RECOMMENDATIONS */}
            {nearbyAttractions.map((item, idx) => (
              item.latitude && item.longitude && (
                <Marker
                  key={`nearby-${idx}`}
                  position={[item.latitude, item.longitude]}
                  icon={recommendationIcon}
                  eventHandlers={{
                    click: () => {
                      setManualCenter([item.latitude, item.longitude]);
                    },
                  }}
                >
                  <Popup>
                    <div style={{ fontWeight: 'bold', color: '#f0a038' }}>✨ Nearby Gem</div>
                    <div style={{ fontWeight: 'bold' }}>{item.name}</div>
                    <div style={{ fontSize: '12px', color: 'var(--muted)' }}>{item.category}</div>
                    <p style={{ marginTop: '8px', fontSize: '13px' }}>{item.description}</p>
                  </Popup>
                </Marker>
              )
            ))}
          </MapContainer>

          {loading && (
            <div style={{
              position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
              background: 'rgba(255,255,255,0.5)', zIndex: 1000,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              borderRadius: '24px'
            }}>
              Loading route...
            </div>
          )}
        </div>

        <div className="map-sidebar">
          <div className="sidebar-header">
            <h3>{trip?.title}</h3>
            <p className="trip-sub">Day {selectedDay} Overview</p>
          </div>

          <div className="route-meta-web">
            <div className="meta-item">
              <div className="val-row">
                <strong>{routeData.totalLocations}</strong>
                <span className="unit">sites</span>
              </div>
              <span className="label">Total Locations</span>
            </div>
            <div className="meta-item">
              <div className="val-row">
                <strong>{Number(routeData.estimatedDistanceKm || 0).toFixed(1)}</strong>
                <span className="unit">km</span>
              </div>
              <span className="label">Est. Distance</span>
            </div>
            <div className="meta-item">
              <div className="val-row">
                <strong>{routeData.travelTimeMinutes}</strong>
                <span className="unit">min</span>
              </div>
              <span className="label">Travel Time</span>
            </div>
          </div>

          <div className="place-list-web">
            {routeData.locations.map((item, idx) => {
              const isFocused = manualCenter && manualCenter[0] === item.latitude && manualCenter[1] === item.longitude;
              return (
                <div
                  key={item.id || idx}
                  className={`place-item-web ${isFocused ? 'active' : ''}`}
                  onClick={() => item.latitude && item.longitude && setManualCenter([item.latitude, item.longitude])}
                  style={{ cursor: item.latitude ? 'pointer' : 'default' }}
                >
                  <div className="num">{idx + 1}</div>
                  <div className="info">
                    <h4>{item.name}</h4>
                    <p>{item.time} | {item.activityType}</p>
                  </div>
                </div>
              );
            })}
            {routeData.locations.length === 0 && !loading && (
               <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--muted)' }}>
                 No activities scheduled for today.
               </div>
            )}
          </div>

          <div className="sidebar-footer">
            <button
              className="btn-primary"
              style={{ width: '100%', borderRadius: '16px', padding: '16px' }}
              onClick={handleSyncNavigation}
              disabled={!routeData.googleMapsUrl || loading}
            >
              Open in Google Maps
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MapPage;
