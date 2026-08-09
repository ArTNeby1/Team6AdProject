import React, { useMemo } from 'react';
import { useTrip } from '../context/TripContext';

const MapPage = () => {
  const { getActiveTrip } = useTrip();
  const trip = getActiveTrip();

  // If no trip or no locations, show empty state
  const locations = useMemo(() => {
    return trip?.locations || [];
  }, [trip]);

  // Simple coordinate to SVG mapping (Normalizing lat/lng to a 0-800 scale for mock display)
  const markers = useMemo(() => {
    if (locations.length === 0) return [];

    const lats = locations.map(l => l.lat).filter(l => l !== 0);
    const lngs = locations.map(l => l.lng).filter(l => l !== 0);

    const minLat = Math.min(...lats, 18.7);
    const maxLat = Math.max(...lats, 18.8);
    const minLng = Math.min(...lngs, 98.9);
    const maxLng = Math.max(...lngs, 99.0);

    return locations.map((loc, i) => {
      // Linear mapping
      const x = ((loc.lng - minLng) / (maxLng - minLng)) * 600 + 100;
      const y = 600 - (((loc.lat - minLat) / (maxLat - minLat)) * 400 + 100);
      return { ...loc, x, y };
    });
  }, [locations]);

  return (
    <div className="map-page">
      <header className="page-header" style={{marginBottom: '32px'}}>
        <h1>Explore Map: {trip?.title || 'No Trip Selected'}</h1>
        <p>Real-time coordinate visualization from backend data.</p>
      </header>

      <div className="map-container">
        <div className="map-view">
          <svg viewBox="0 0 800 600" style={{width: '100%', height: '100%', display: 'block', background: '#f0ede5'}}>
            <path d="M0,100 L800,100 M0,300 L800,300 M0,500 L800,500 M200,0 L200,600 M400,0 L400,600 M600,0 L600,600" stroke="#e0d8c8" strokeWidth="1" />

            {/* Dynamic Route Line */}
            {markers.length > 1 && (
              <polyline
                points={markers.map(m => `${m.x},${m.y}`).join(' ')}
                fill="none"
                stroke="#0E9E8E"
                strokeWidth="4"
                strokeDasharray="10 5"
              />
            )}

            {/* Dynamic Markers */}
            {markers.map((m, i) => (
              <g key={i} transform={`translate(${m.x}, ${m.y})`}>
                <circle r="12" fill={i === 0 ? "#F0A038" : "#0E9E8E"} />
                <text y="-20" textAnchor="middle" style={{fontSize: '12px', fontWeight: 700}}>{m.name}</text>
              </g>
            ))}
          </svg>
        </div>

        <div className="map-sidebar">
          <h3>{trip?.title}</h3>
          <div className="route-meta-web">
            <div className="meta-item">
              <strong>{locations.length} sites</strong>
              <span>Total Locations</span>
            </div>
          </div>

          <div className="place-list-web" style={{marginTop: '32px', overflowY: 'auto', maxHeight: '400px'}}>
            {locations.map((item, idx) => (
              <div key={item.id} className="place-item-web">
                <div className="num">{idx + 1}</div>
                <div className="info">
                  <h4>{item.name}</h4>
                  <p>{item.time} | Lat: {item.lat.toFixed(4)}, Lng: {item.lng.toFixed(4)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default MapPage;
