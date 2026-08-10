import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';

const ItineraryListPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trips, setActiveTripId, createNewTrip, loadingTrips, tripsError, refreshTrips } = useTrip();
  const [creating, setCreating] = useState(false);

  const handleTripClick = (id) => {
    setActiveTripId(id);
    navigate(`/itinerary/${id}`);
  };

  const handleCreateTrip = async () => {
    setCreating(true);
    try {
      const id = await createNewTrip([], {
        travelStyle: user?.travelStyle || 'Cultural',
        preferTransport: user?.preferTransport || 'Public',
      }, { tripName: 'New trip', durationDays: 3 });
      navigate(`/itinerary/${id}`);
    } catch (err) {
      alert(err.message || 'Failed to create trip');
    } finally {
      setCreating(false);
    }
  };

  const activeTrips = trips.filter((t) => t.status === 'ACTIVE' || !t.status);
  const upcomingTrips = trips.filter((t) => t.status === 'NOT_STARTED');
  const finishedTrips = trips.filter((t) => t.status === 'FINISHED');

  return (
    <div className="itinerary-list-page">
      <header className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>My Itineraries</h1>
        <button type="button" className="btn-primary" onClick={handleCreateTrip} disabled={creating}>
          {creating ? 'Creating...' : '+ New Trip'}
        </button>
      </header>

      {loadingTrips && <p style={{ color: 'var(--muted)' }}>Loading trips...</p>}
      {tripsError && (
        <p style={{ color: '#b42318' }}>
          {tripsError}{' '}
          <button type="button" className="btn-secondary" onClick={() => refreshTrips().catch(() => {})}>
            Retry
          </button>
        </p>
      )}
      {!loadingTrips && !tripsError && trips.length === 0 && (
        <p style={{ color: 'var(--muted)', marginBottom: '24px' }}>
          No trips yet. Create one or start from Smart Planning.
        </p>
      )}

      <div className="itinerary-grid">
        {activeTrips.length > 0 && (
          <div className="itinerary-section">
            <h2 className="itinerary-section-title active">Active</h2>
            {activeTrips.map(trip => (
              <div key={trip.id} className="itinerary-card-active" onClick={() => handleTripClick(trip.id)}>
                <div className="itinerary-icon-lg" style={{
                  overflow: 'hidden',
                  background: trip.coverImage ? 'none' : 'linear-gradient(135deg, var(--jade), var(--jade-deep))'
                }}>
                  {trip.coverImage ? (
                    <img src={trip.coverImage} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    String(trip.shortName || 'T').split(' ').map((word, i) => (
                      <div key={i}>{word}</div>
                    ))
                  )}
                </div>
                <div className="itinerary-content">
                  <h3>{trip.title}</h3>
                  <div className="date">{trip.date}</div>
                  <div className="status-text">Active</div>
                  <div className="progress-bar">
                    <div className="progress" style={{ width: `${(trip.progress || 0) * 100}%` }}></div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* NOT STARTED SECTION */}
        {upcomingTrips.length > 0 && (
          <div className="itinerary-section">
            <h2 className="itinerary-section-title">Upcoming</h2>
            {upcomingTrips.map(trip => (
              <div key={trip.id} className="itinerary-card-simple" onClick={() => handleTripClick(trip.id)}>
                <div className="itinerary-icon-sm" style={{ overflow: 'hidden', background: trip.coverImage ? 'none' : 'var(--mint)' }}>
                  {trip.coverImage ? (
                    <img src={trip.coverImage} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    trip.shortName
                  )}
                </div>
                <div className="itinerary-info">
                  <h3>{trip.title}</h3>
                  <p>{trip.desc}</p>
                </div>
                <div className="itinerary-date-right">{trip.date}</div>
              </div>
            ))}
          </div>
        )}

        {/* FINISHED SECTION */}
        {finishedTrips.length > 0 && (
          <div className="itinerary-section">
            <h2 className="itinerary-section-title finished">Finished</h2>
            {finishedTrips.map(trip => (
              <div key={trip.id} className="itinerary-card-simple" onClick={() => handleTripClick(trip.id)}>
                <div className="itinerary-icon-sm finished" style={{ overflow: 'hidden', background: trip.coverImage ? 'none' : 'var(--line-soft)' }}>
                  {trip.coverImage ? (
                    <img src={trip.coverImage} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    trip.shortName
                  )}
                </div>
                <div className="itinerary-info">
                  <h3>{trip.title}</h3>
                  <p>{trip.desc}</p>
                </div>
                <div className="itinerary-date-right">{trip.date}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ItineraryListPage;
