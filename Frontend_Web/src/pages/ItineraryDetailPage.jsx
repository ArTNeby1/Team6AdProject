import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { mapApi } from '../services/api';

const ItineraryDetailPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const routerLocation = useLocation();
  const {
    getTripById,
    getActiveTrip,
    setActiveTripId,
    addDayToTrip,
    addLocationsToTripDay,
    updateTripTitle,
    updateTripDate,
    loadingTrips,
  } = useTrip();

  const trip = (id && getTripById(id)) || getActiveTrip();

  const [selectedDay, setSelectedDay] = useState(1);
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitleValue, setEditTitleValue] = useState('');
  const [routeStats, setRouteStats] = useState({ distance: 0, time: 0, transports: [] });

  // F-18: nearby/similar place suggestions from the AI /recommend agent, handed off from
  // ImportPage's confirm step via navigation state. Backend doesn't persist these, so a
  // page refresh loses them — that matches the backend's "ephemeral suggestion" design.
  const [suggestions, setSuggestions] = useState(routerLocation.state?.suggestedAdditions || []);
  const weatherSummary = routerLocation.state?.weatherSummary;
  const [addingSuggestion, setAddingSuggestion] = useState(null);

  const handleAddSuggestion = async (suggestion) => {
    if (!trip) return;
    setAddingSuggestion(suggestion.name);
    try {
      // Pass the suggestion's own coordinates so the backend can insert it next to
      // whichever existing stop it was actually recommended for being near (e.g. next to
      // Chinatown), instead of tacking it onto the end of the day.
      const nearCoords = suggestion.latitude != null && suggestion.longitude != null
        ? { lat: suggestion.latitude, lng: suggestion.longitude }
        : null;
      await addLocationsToTripDay(trip.id, selectedDay, [suggestion.name], nearCoords);
      setSuggestions(prev => prev.filter(s => s.name !== suggestion.name));
    } catch {
      alert('Failed to add place. Please try again.');
    } finally {
      setAddingSuggestion(null);
    }
  };

  // AI Summary State from Import flow
  const [aiSummary, setAiSummary] = useState(location.state?.showAiSummary ? location.state : null);
  const [selectedGems, setSelectedGems] = useState(new Set());
  const [showCopyAlert, setShowCopyAlert] = useState(false);

  const toggleGem = (name) => {
    setSelectedGems(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  const handleExploreJourney = async () => {
    if (selectedGems.size > 0) {
      await addLocationsToTripDay(trip.id, 1, Array.from(selectedGems));
    }
    setAiSummary(null);
  };

  useEffect(() => {
    if (id) setActiveTripId(id);
  }, [id, setActiveTripId]);

  useEffect(() => {
    if (trip) {
      setEditTitleValue(trip.title);
    }
  }, [trip]);

  // Fetch route stats for the selected day
  useEffect(() => {
    if (trip?.id) {
      mapApi.getRoute(trip.id, selectedDay)
        .then(res => {
          setRouteStats({
            distance: res.data.totalDistanceKm || 0,
            time: res.data.totalDurationMinutes || 0,
            transports: res.data.transports || []
          });
        })
        .catch(() => {
          setRouteStats({ distance: 0, time: 0, transports: [] });
        });
    }
  }, [trip?.id, selectedDay, trip?.locations]);

  if (loadingTrips && !trip) return <div>Loading trip...</div>;
  if (!trip) return <div>Trip not found</div>;

  const handleAddDay = () => {
    addDayToTrip(trip.id);
  };

  const handleSaveTitle = () => {
    if (editTitleValue.trim()) {
      updateTripTitle(trip.id, editTitleValue.trim());
      setIsEditingTitle(false);
    }
  };

  const handleShare = () => {
    // Current URL as the share link
    const shareUrl = window.location.href;

    navigator.clipboard.writeText(shareUrl).then(() => {
      setShowCopyAlert(true);
      setTimeout(() => setShowCopyAlert(false), 3000); // Auto-hide after 3s
    }).catch(err => {
      console.error('Failed to copy link:', err);
    });
  };

  const dayLocations = trip.locations.filter(loc => loc.day === selectedDay);

  return (
    <div className="route-page">
      {/* COPY NOTIFICATION TOAST */}
      {showCopyAlert && (
        <div style={{
          position: 'fixed', top: '100px', left: '50%', transform: 'translateX(-50%)',
          background: 'var(--ink)', color: '#fff', padding: '12px 24px',
          borderRadius: '12px', zIndex: 9999, fontWeight: '700',
          boxShadow: '0 8px 30px rgba(0,0,0,0.2)', animation: 'slideDown 0.3s ease-out'
        }}>
          ✅ Trip URL copied to clipboard!
        </div>
      )}

      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <button className="btn-secondary" style={{alignSelf: 'flex-start', padding: '6px 16px', marginBottom: '12px', fontSize: '14px'}} onClick={() => navigate('/route')}>
            ← Back to List
          </button>
          <div className="kicker" style={{color: 'var(--muted)', fontSize: '14px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '2px'}}>My Trip Itinerary</div>

          <div className="title-area" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            {isEditingTitle ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <input
                  type="text"
                  value={editTitleValue}
                  onChange={(e) => setEditTitleValue(e.target.value)}
                  style={{
                    fontSize: '32px', fontWeight: 'bold', border: '2px solid var(--jade)',
                    borderRadius: '8px', padding: '4px 12px', outline: 'none',
                    fontFamily: 'var(--display)'
                  }}
                  autoFocus
                  onBlur={handleSaveTitle}
                  onKeyDown={(e) => e.key === 'Enter' && handleSaveTitle()}
                />
              </div>
            ) : (
              <>
                <h1 style={{ margin: 0 }}>{trip.title}</h1>
                {trip.status !== 'FINISHED' && (
                  <button
                    onClick={() => setIsEditingTitle(true)}
                    style={{
                      background: 'none', border: 'none', cursor: 'pointer',
                      fontSize: '20px', color: 'var(--jade)', padding: '4px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      opacity: 0.6, transition: 'opacity 0.2s'
                    }}
                    onMouseOver={(e) => e.currentTarget.style.opacity = '1'}
                    onMouseOut={(e) => e.currentTarget.style.opacity = '0.6'}
                    title="Edit trip title"
                  >
                    ✏️
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        <div className="header-actions" style={{display: 'flex', gap: '12px', alignItems: 'center', height: 'fit-content', marginTop: 'auto'}}>
          {/* START DATE MODIFIER MOVED HERE */}
          <div className="trip-date-modifier" style={{ cursor: trip.status !== 'FINISHED' ? 'pointer' : 'default' }}>
            <span className="label">Start Time:</span>
            <span className="date-val">{trip.date}</span>
            <span className="cal-icon">📅</span>
            {trip.status !== 'FINISHED' && (
              <input
                type="date"
                min={new Date().toISOString().split('T')[0]}
                onChange={(e) => {
                  if (e.target.value) {
                    updateTripDate(trip.id, e.target.value);
                  }
                }}
                title="Click to change start date"
              />
            )}
          </div>

          {trip.status !== 'FINISHED' ? (
            <>
              <button className="btn-shadow-style" onClick={() => navigate('/edit')}>Edit Itinerary</button>
              <button className="btn-shadow-style" onClick={() => navigate('/map')}>View on Map</button>
              <button className="btn-shadow-style" onClick={() => navigate('/route')}>Save</button>
            </>
          ) : (
            <>
              <button className="btn-shadow-style" onClick={() => navigate('/map')}>View on Map</button>
            </>
          )}
        </div>
      </header>

      <div className="route-grid">
        <div className="itinerary-sidebar">
          <div className="info-card">
            <button
              className="btn-primary"
              style={{
                position: 'absolute',
                top: '24px',
                right: '24px',
                padding: '8px 20px',
                fontSize: '14px',
                borderRadius: '12px'
              }}
              onClick={handleShare}
            >
              Share
            </button>
            <h3>Trip Overview</h3>
            <div className="aibadge" style={{background: '#FCEFD6', border: '1px solid #F3DDAF', padding: '12px', borderRadius: '12px', margin: '16px 0', fontSize: '14px'}}>
              <span className="s">🪄</span>
              <span className="t" style={{color: '#8a5a10', fontWeight: 700}}> AI has optimized the route based on preferences</span>
            </div>
            <div className="route-stats" style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px'}}>
              <div className="stat-box">
                <div className="stat-label">Est. Distance</div>
                <div className="stat-val">{Number(routeStats.distance || 0).toFixed(1)}km</div>
              </div>
              <div className="stat-box">
                <div className="stat-label">Day {selectedDay} Sites</div>
                <div className="stat-val">{dayLocations.length} sites</div>
              </div>
            </div>

            {(trip.travelStyle || trip.preferTransport) && (
              <div style={{ display: 'flex', gap: '8px', marginTop: '16px', flexWrap: 'wrap' }}>
                {trip.travelStyle && (
                  <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                    {trip.travelStyle}
                  </span>
                )}
                {trip.preferTransport && (
                  <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                    {trip.preferTransport}
                  </span>
                )}
              </div>
            )}

            <div className="day-tabs" style={{marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '8px'}}>
              {Array.from({ length: trip.dayCount || 1 }).map((_, i) => (
                <div
                  key={i}
                  className={`day-tab ${selectedDay === i + 1 ? 'on' : ''}`}
                  onClick={() => setSelectedDay(i + 1)}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                >
                  <span>Day {i + 1}: {trip.locations.some(loc => loc.day === i + 1) ? 'Planning' : 'To be planned'}</span>
                  {i === (trip.dayCount || 1) - 1 && trip.status !== 'FINISHED' && (
                    <span
                      onClick={(e) => { e.stopPropagation(); handleAddDay(); }}
                      style={{
                        background: 'var(--jade)', color: '#fff',
                        width: '24px', height: '24px', borderRadius: '50%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '20px', fontWeight: '500', marginLeft: '10px',
                        cursor: 'pointer',
                        lineHeight: '0',
                        paddingBottom: '3px'
                      }}
                    >+</span>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* F-18: AI-recommended nearby/similar places, offered once right after confirm */}
          {suggestions.length > 0 && (
            <div className="info-card" style={{ marginTop: '20px' }}>
              <h3>Recommended Nearby Places</h3>
              {weatherSummary && (
                <p style={{ color: 'var(--muted)', fontSize: '13px', marginTop: '4px' }}>{weatherSummary}</p>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '12px' }}>
                {suggestions.map((s) => (
                  <div key={s.name} style={{ border: '1px solid var(--line-soft)', borderRadius: '12px', padding: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                      <div>
                        <strong>{s.name}</strong>
                        {s.distanceKm != null && (
                          <span style={{ color: 'var(--muted)', fontSize: '13px' }}> · {s.distanceKm.toFixed(1)}km away</span>
                        )}
                      </div>
                      <button
                        className="btn-secondary"
                        style={{ padding: '4px 12px', fontSize: '13px', whiteSpace: 'nowrap' }}
                        onClick={() => handleAddSuggestion(s)}
                        disabled={addingSuggestion === s.name}
                      >
                        {addingSuggestion === s.name ? 'Adding...' : '+ Add'}
                      </button>
                    </div>
                    {s.reason && <p style={{ fontSize: '13px', color: 'var(--muted)', marginTop: '6px' }}>{s.reason}</p>}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="itinerary-main">
          <div className="day-section">
            <div className="day-header">
              <span>{selectedDay}</span>
              Day {selectedDay}: {trip.date.split('-')[0].trim()}
            </div>

            <div className="timeline">
              {dayLocations.length === 0 ? (
                <div style={{padding: '40px', textAlign: 'center', background: '#fff', borderRadius: '20px', border: '1px dashed var(--line)'}}>
                  <p style={{color: 'var(--muted)'}}>No attractions for this day, go import or add some!</p>
                  <button className="btn-primary" style={{marginTop: '20px'}} onClick={() => navigate(`/import?tripId=${trip.id}&day=${selectedDay}`)}>Import Trip</button>
                </div>
              ) : (
                dayLocations.map((item, idx) => (
                  <React.Fragment key={item.id}>
                    <div className="tl-node-row">
                      <div className="tl-left">
                        <div className="tl-circle">{idx + 1}</div>
                        {idx < trip.locations.length - 1 && <div className="tl-line"></div>}
                      </div>
                      <div className="tl-content">
                        <div className="tl-card-web">
                          <div className="tl-time">{item.time || '10:00'}</div>
                          <div className="tl-info">
                            <h3>{item.name}</h3>
                            <p>Duration {item.duration || '1.5'}h · Activity</p>
                          </div>
                        </div>
                      </div>
                    </div>

                    {idx < dayLocations.length - 1 && (
                      <div className="tl-transport">
                        <div className="tl-left"><div className="tl-line-dotted"></div></div>
                        <div className="tl-trans-info" style={{ flexWrap: 'wrap', gap: '16px' }}>
                          {(() => {
                            // Backend now returns one entry per travel mode for the same
                            // prev/next pair (driving/walking/transit/bicycling, F-14,
                            // 2026-08-16) instead of a single guessed one — show all four
                            // instead of picking one.
                            const legTransports = routeStats.transports.filter(
                              t => t.prevScheduleId.toString() === item.id.toString() &&
                                   t.nextScheduleId.toString() === dayLocations[idx + 1].id.toString()
                            );
                            if (legTransports.length === 0) {
                              return <span>🚕 15 min Transport</span>;
                            }
                            const iconMap = {
                              walking: '🚶',
                              driving: '🚕',
                              transit: '🚌',
                              bicycling: '🚲'
                            };
                            // Fixed display order — transportType is now always one of these
                            // four clean labels (see TripService#estimateRoute), backend's
                            // array order isn't guaranteed to match what reads best here.
                            const modeOrder = ['driving', 'walking', 'transit', 'bicycling'];
                            const sorted = [...legTransports].sort(
                              (a, b) => modeOrder.indexOf(a.transportType) - modeOrder.indexOf(b.transportType)
                            );
                            const btnBaseStyle = {
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '6px',
                              padding: '6px 12px',
                              borderRadius: '999px',
                              fontSize: '13px',
                              fontWeight: 600,
                              lineHeight: 1,
                              whiteSpace: 'nowrap'
                            };
                            return sorted.map(transport => {
                              const label = (
                                <>
                                  <span>{iconMap[transport.transportType] || '🚕'}</span>
                                  <span>{transport.durationMinutes} min {transport.transportType}</span>
                                </>
                              );
                              return transport.googleMapLink ? (
                                <a
                                  key={transport.id}
                                  href={transport.googleMapLink}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  title={`Open ${transport.transportType} route in Google Maps`}
                                  style={{
                                    ...btnBaseStyle,
                                    color: 'var(--jade-deep)',
                                    background: 'var(--mint)',
                                    border: '1px solid transparent',
                                    textDecoration: 'none',
                                    cursor: 'pointer',
                                    transition: 'background 0.15s ease, border-color 0.15s ease'
                                  }}
                                  onMouseEnter={e => {
                                    e.currentTarget.style.background = 'var(--jade)';
                                    e.currentTarget.style.color = '#fff';
                                  }}
                                  onMouseLeave={e => {
                                    e.currentTarget.style.background = 'var(--mint)';
                                    e.currentTarget.style.color = 'var(--jade-deep)';
                                  }}
                                >
                                  {label}
                                  <span aria-hidden="true" style={{ fontSize: '11px', opacity: 0.75 }}>↗</span>
                                </a>
                              ) : (
                                <span
                                  key={transport.id}
                                  style={{
                                    ...btnBaseStyle,
                                    color: 'var(--muted)',
                                    background: 'var(--line-soft)'
                                  }}
                                >
                                  {label}
                                </span>
                              );
                            });
                          })()}
                        </div>
                      </div>
                    )}
                  </React.Fragment>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {/* AI GENERATED SUMMARY MODAL */}
      {aiSummary && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(20, 48, 74, 0.8)',
          zIndex: 5000, display: 'flex', alignItems: 'center', justifyContent: 'center',
          backdropFilter: 'blur(10px)'
        }}>
          <div className="info-card" style={{ maxWidth: '600px', width: '90%', padding: '40px', textAlign: 'center', position: 'relative' }}>
            <div style={{ fontSize: '64px', marginBottom: '20px' }}>✨</div>
            <h2 style={{ fontSize: '28px', marginBottom: '16px' }}>Your AI Itinerary is Ready!</h2>

            <div style={{ background: 'var(--mint)', padding: '20px', borderRadius: '16px', marginBottom: '32px', textAlign: 'left' }}>
               <h4 style={{ color: 'var(--jade-deep)', marginBottom: '8px' }}>🌦️ Local Weather Insight</h4>
               <p style={{ color: 'var(--ink-70)', lineHeight: '1.6' }}>{aiSummary.weatherSummary || "Clear skies ahead! Perfect for sightseeing."}</p>
            </div>

            {aiSummary.suggestedAdditions && aiSummary.suggestedAdditions.length > 0 && (
              <div style={{ textAlign: 'left', marginBottom: '32px' }}>
                <h4 style={{ marginBottom: '12px' }}>💡 Nearby Gems you might like:</h4>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                  {aiSummary.suggestedAdditions.map((item, idx) => {
                    const name = item.name || item;
                    const isSelected = selectedGems.has(name);
                    return (
                      <span
                        key={idx}
                        onClick={() => toggleGem(name)}
                        style={{
                          padding: '8px 16px',
                          background: isSelected ? 'var(--jade)' : 'var(--paper)',
                          color: isSelected ? '#fff' : 'var(--ink)',
                          borderRadius: '12px',
                          fontSize: '14px',
                          cursor: 'pointer',
                          transition: 'all 0.2s',
                          border: isSelected ? '1px solid var(--jade)' : '1px solid var(--line-soft)',
                          fontWeight: '600'
                        }}
                      >
                        {isSelected ? '✓ ' : '📍 '} {name}
                      </span>
                    );
                  })}
                </div>
              </div>
            )}

            <button className="btn-primary" style={{ width: '100%', padding: '16px' }} onClick={handleExploreJourney}>
              Explore My Journey {selectedGems.size > 0 && `(+${selectedGems.size})`}
            </button>
          </div>
        </div>
      )}

    </div>
  );
};

export default ItineraryDetailPage;
