import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const ItineraryDetailPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams();
  const {
    getTripById,
    getActiveTrip,
    setActiveTripId,
    addDayToTrip,
    updateTripTitle,
    updateTripDate,
    addLocationsToTripDay,
    updateTripCover,
    loadingTrips,
  } = useTrip();

  const trip = (id && getTripById(id)) || getActiveTrip();

  const [selectedDay, setSelectedDay] = useState(1);
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitleValue, setEditTitleValue] = useState('');
  const [showImageModal, setShowImageModal] = useState(false);

  const dateInputRef = useRef(null);

  // AI Summary State from Import flow
  const [aiSummary, setAiSummary] = useState(location.state?.showAiSummary ? location.state : null);
  const [selectedGems, setSelectedGems] = useState(new Set());

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

  const handleDateClick = () => {
    if (dateInputRef.current && typeof dateInputRef.current.showPicker === 'function') {
      dateInputRef.current.showPicker();
    }
  };

  return (
    <div className="route-page">
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
          <div className="trip-date-modifier" style={{ cursor: 'default' }}>
            <span className="label">Start Time:</span>
            <span className="date-val">{trip.date}</span>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <span
                className="cal-icon"
                style={{ cursor: 'pointer', fontSize: '18px' }}
              >
                📅
              </span>
              {trip.status !== 'FINISHED' && (
                <input
                  type="date"
                  min={new Date().toISOString().split('T')[0]}
                  onChange={(e) => {
                    if (e.target.value) {
                      updateTripDate(trip.id, e.target.value);
                    }
                  }}
                  style={{
                    position: 'absolute',
                    inset: 0,
                    opacity: 0,
                    cursor: 'pointer',
                    zIndex: 20
                  }}
                  title="Click to change start date"
                />
              )}
            </div>
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
