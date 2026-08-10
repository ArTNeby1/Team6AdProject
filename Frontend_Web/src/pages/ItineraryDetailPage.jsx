import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const ItineraryDetailPage = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const routerLocation = useLocation();
  const {
    getTripById,
    getActiveTrip,
    setActiveTripId,
    addDayToTrip,
    addLocationsToTripDay,
    updateTripTitle,
    updateTripCover,
    loadingTrips,
  } = useTrip();

  const trip = (id && getTripById(id)) || getActiveTrip();

  const [selectedDay, setSelectedDay] = useState(1);
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitleValue, setEditTitleValue] = useState('');
  const [showImageModal, setShowImageModal] = useState(false);

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
      await addLocationsToTripDay(trip.id, selectedDay, [suggestion.name]);
      setSuggestions(prev => prev.filter(s => s.name !== suggestion.name));
    } catch {
      alert('Failed to add place. Please try again.');
    } finally {
      setAddingSuggestion(null);
    }
  };

  const fileInputRef = useRef(null);

  useEffect(() => {
    if (id) setActiveTripId(id);
  }, [id, setActiveTripId]);

  useEffect(() => {
    if (trip) {
      setEditTitleValue(trip.title);
    }
  }, [trip]);

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

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        updateTripCover(trip.id, reader.result);
      };
      reader.readAsDataURL(file);
    }
  };

  const triggerFileUpload = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    fileInputRef.current.click();
  };

  const dayLocations = trip.locations.filter(loc => loc.day === selectedDay);

  return (
    <div className="route-page">
      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end'}}>
        <div style={{ flex: 1 }}>
          <button className="btn-secondary" style={{padding: '6px 16px', marginBottom: '12px', fontSize: '14px'}} onClick={() => navigate('/route')}>
            ← Back to List
          </button>
          <div className="kicker" style={{color: 'var(--muted)', fontSize: '14px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '2px'}}>My Trip Itinerary</div>

          <div className="title-area" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
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

        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          {trip.status !== 'FINISHED' ? (
            <>
              <button className="btn-secondary" onClick={() => navigate('/edit')}>Edit Itinerary</button>
              <button className="btn-secondary" onClick={() => navigate('/map')}>View on Map</button>
              <button className="btn-secondary" onClick={() => navigate('/route')}>Save</button>
              <button className="btn-primary">Share</button>
            </>
          ) : (
            <>
              <button className="btn-secondary" onClick={() => navigate('/map')}>View on Map</button>
              <button className="btn-primary">Share</button>
            </>
          )}
        </div>
      </header>

      <div className="route-grid">
        <div className="itinerary-sidebar">
          {/* TRIP COVER IMAGE */}
          <div className="trip-cover-container">
            {trip.coverImage ? (
              <img
                src={trip.coverImage}
                alt="Trip Cover"
                className="trip-cover-img"
                onClick={() => setShowImageModal(true)}
                style={{ cursor: 'zoom-in' }}
              />
            ) : (
              <div className="trip-cover-empty" onClick={triggerFileUpload}>
                <span style={{ fontSize: '32px' }}>🖼️</span>
                <span>Click to upload cover photo</span>
              </div>
            )}

            {/* HIDDEN FILE INPUT */}
            <input
              type="file"
              ref={fileInputRef}
              style={{ display: 'none' }}
              accept="image/*"
              onChange={handleFileChange}
            />

            {/* PENCIL OVERLAY - Always allow changing cover photo even for finished trips */}
            <div className="edit-cover-btn" onClick={triggerFileUpload} title="Change cover photo">
              ✏️
            </div>
          </div>

          <div className="info-card">
            <h3>Trip Overview</h3>
            <div className="aibadge" style={{background: '#FCEFD6', border: '1px solid #F3DDAF', padding: '12px', borderRadius: '12px', margin: '16px 0', fontSize: '14px'}}>
              <span className="s">🪄</span>
              <span className="t" style={{color: '#8a5a10', fontWeight: 700}}> AI has optimized the route based on preferences</span>
            </div>
            <div className="route-stats" style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px'}}>
              <div className="stat-box">
                <div className="stat-label">Total Distance</div>
                <div className="stat-val">15.4km</div>
              </div>
              <div className="stat-box">
                <div className="stat-label">Total Locations</div>
                <div className="stat-val">{trip.locations.length} sites</div>
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
                  <span>Day {i + 1}: {i === 0 ? 'Planning' : 'To be planned'}</span>
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
                            <p>Suggested Duration {item.duration || '1.5h'} · {idx === 0 ? 'Starting Point' : 'Check-in Point'}</p>
                          </div>
                          <div className="tl-actions">
                            <button onClick={() => navigate(`/attraction/${encodeURIComponent(item.name)}`)}>View Details</button>
                          </div>
                        </div>
                      </div>
                    </div>

                    {idx < dayLocations.length - 1 && (
                      <div className="tl-transport">
                        <div className="tl-left"><div className="tl-line-dotted"></div></div>
                        <div className="tl-trans-info">
                          <span>{item.transport || '🚕 15 min Transport'}</span>
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

      {/* IMAGE ENLARGE MODAL */}
      {showImageModal && (
        <div className="image-modal-overlay" onClick={() => setShowImageModal(false)}>
          <img src={trip.coverImage} alt="Enlarged view" className="image-modal-content" />
        </div>
      )}
    </div>
  );
};

export default ItineraryDetailPage;
