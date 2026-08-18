import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';

const ItineraryListPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trips, setActiveTripId, createNewTrip, deleteTrip, loadingTrips, tripsError, refreshTrips } = useTrip();
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
      });
      navigate(`/itinerary/${id}`);
    } catch (err) {
      alert(err.message || 'Failed to create trip');
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteTrip = async (e, id) => {
    e.stopPropagation();
    if (window.confirm('Are you sure you want to delete this itinerary? This action cannot be undone.')) {
      try {
        await deleteTrip(id);
      } catch {
        alert('Failed to delete trip. Please try again.');
      }
    }
  };

  const sortByDate = (a, b) => {
    const dateA = new Date(a.date);
    const dateB = new Date(b.date);
    return dateA - dateB;
  };

  const activeTrips = trips.filter(t => t.status === 'ACTIVE').sort(sortByDate);
  const upcomingTrips = trips.filter(t => t.status === 'NOT_STARTED').sort(sortByDate);
  const finishedTrips = trips.filter(t => t.status === 'FINISHED').sort((a, b) => sortByDate(b, a)); // Reverse for finished

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

      <div className="itinerary-grid">
        {/* ACTIVE SECTION */}
        {activeTrips.length > 0 && (
          <div className="itinerary-section">
            <h2 className="itinerary-section-title active">Active</h2>
            {activeTrips.map(trip => (
              <div key={trip.id} className="itinerary-card-active" onClick={() => handleTripClick(trip.id)}>
                <div className="itinerary-icon-lg" style={{
                  overflow: 'hidden',
                  background: 'linear-gradient(135deg, var(--jade), var(--jade-deep))'
                }}>
                  {String(trip.shortName || 'T').split(' ').map((word, i) => (
                    <div key={i}>{word}</div>
                  ))}
                </div>
                <div className="itinerary-content">
                  <h3>{trip.title}</h3>
                  <div className="date">{trip.date}</div>
                  <div className="status-text">Active</div>
                  <div className="progress-bar">
                    <div className="progress" style={{ width: `${(trip.progress || 0) * 100}%` }}></div>
                  </div>
                </div>
                <button
                  className="delete-trip-btn"
                  onClick={(e) => handleDeleteTrip(e, trip.id)}
                  title="Delete Itinerary"
                >🗑️</button>
              </div>
            ))}
          </div>
        )}

        {/* UPCOMING SECTION */}
        {upcomingTrips.length > 0 && (
          <div className="itinerary-section">
            <h2 className="itinerary-section-title">Upcoming</h2>
            {upcomingTrips.map(trip => (
              <div key={trip.id} className="itinerary-card-simple" onClick={() => handleTripClick(trip.id)}>
                <div className="itinerary-icon-sm" style={{ overflow: 'hidden', background: 'var(--mint)' }}>
                  {trip.shortName}
                </div>
                <div className="itinerary-info">
                  <h3>{trip.title}</h3>
                  <p>{trip.date}</p>
                </div>
                <div className="itinerary-date-right">{trip.date}</div>
                <button
                  className="delete-trip-btn sm"
                  onClick={(e) => handleDeleteTrip(e, trip.id)}
                  title="Delete Itinerary"
                >🗑️</button>
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
                <div className="itinerary-icon-sm finished" style={{ overflow: 'hidden', background: 'var(--line-soft)' }}>
                  {trip.shortName}
                </div>
                <div className="itinerary-info">
                  <h3>{trip.title}</h3>
                  <p>{trip.date}</p>
                </div>
                <div className="itinerary-date-right">{trip.date}</div>
                <button
                  className="delete-trip-btn sm"
                  onClick={(e) => handleDeleteTrip(e, trip.id)}
                  title="Delete Itinerary"
                >🗑️</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ItineraryListPage;
