import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';

const AttractionPage = () => {
  const { name } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trips, addLocationsToTripDay, fetchAttractionData } = useTrip();

  const [attraction, setAttraction] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [showPicker, setShowPicker] = useState(false);
  const [selectedTripId, setSelectedTripId] = useState('');
  const [selectedDay, setSelectedDay] = useState(1);

  const attractionName = name ? decodeURIComponent(name) : 'Wat Chedi Luang';

  useEffect(() => {
    const loadData = async () => {
      setIsLoading(true);
      const data = await fetchAttractionData(attractionName);
      setAttraction(data);
      setIsLoading(false);
    };
    loadData();
  }, [attractionName]);

  const displayData = useMemo(() => {
    if (attraction) {
      return {
        name: attraction.name,
        enName: attraction.address?.includes(',') ? attraction.address.split(',').pop().trim() : 'LoomyTrip Spot',
        rating: '4.8', // Mocked if not in DB
        reviews: '4.2k',
        tags: [attraction.category || 'Spot'],
        desc: attraction.description || 'A beautiful destination to explore.',
        img: null, // Should come from backend eventually
        details: {
          time: '08:00 - 17:00',
          price: 'Free',
          duration: '1.5 - 2 hours',
          address: attraction.address || 'Address TBD'
        }
      };
    }

    // Fallback for unknown
    const colors = ['#0E9E8E', '#F0A038', '#EF6E5B', '#14304A', '#6B7A80'];
    const colorIndex = attractionName.length % colors.length;
    return {
      name: attractionName,
      enName: 'LoomyTrip Destination',
      rating: '4.0',
      reviews: 'New',
      tags: ['To Explore'],
      desc: 'No detailed introduction yet, feel free to visit and share your experience.',
      placeholderColor: colors[colorIndex],
      details: {
        time: 'TBD',
        price: 'Free',
        duration: '1-2 hours',
        address: 'TBD'
      }
    };
  }, [attraction, attractionName]);

  const eligibleTrips = useMemo(() => {
    return trips.filter(t => t.status === 'ACTIVE' || t.status === 'NOT_STARTED');
  }, [trips]);

  const handleOpenPicker = () => {
    if (!user) {
      navigate('/login');
      return;
    }
    if (eligibleTrips.length === 0) {
      alert('You don\'t have any active or upcoming trips yet, please create one first!');
      return;
    }
    setSelectedTripId(eligibleTrips[0].id);
    setShowPicker(true);
  };

  const handleConfirmAdd = async () => {
    if (!selectedTripId) return;
    await addLocationsToTripDay(selectedTripId, selectedDay, [displayData.name]);
    setShowPicker(false);
    if (window.confirm(`Successfully added ${displayData.name} to the itinerary. View it now?`)) {
      navigate(`/itinerary/${selectedTripId}`);
    }
  };

  if (isLoading) return <div style={{ padding: '100px', textAlign: 'center' }}>Loading attraction details...</div>;

  return (
    <div className="attraction-page">
      <div
        className="attraction-hero"
        style={{
          backgroundImage: displayData.img ? `url(${displayData.img})` : 'none',
          backgroundColor: displayData.placeholderColor || '#0E9E8E'
        }}
      >
        <div className="hero-overlay"></div>
        <div className="hero-content">
          <div className="container">
            <h1>{displayData.name}</h1>
            <p>{displayData.enName}</p>
            <div className="hero-actions" style={{marginTop: '24px', display: 'flex', gap: '16px'}}>
              <button className="btn-primary" onClick={handleOpenPicker}>Add to Itinerary</button>
              <button className="btn-secondary">Favorite Spot</button>
            </div>
          </div>
        </div>
      </div>

      <div className="attraction-layout">
        <div className="attraction-main">
          <div className="info-card">
            <h2>Attraction Introduction</h2>
            <div className="metarow" style={{margin: '16px 0'}}>
              <span className="chip star">★ {displayData.rating} ({displayData.reviews} Reviews)</span>
              {displayData.tags.map((tag, i) => (
                <span key={i} className="chip">{tag}</span>
              ))}
            </div>
            <p className="desc-text">{displayData.desc}</p>
          </div>
        </div>

        <div className="attraction-sidebar">
          <div className="info-card">
            <h3>Practical Information</h3>
            <ul className="details-list">
              <li><strong>Opening Hours: </strong> {displayData.details.time}</li>
              <li><strong>Ticket Price: </strong> {displayData.details.price}</li>
              <li><strong>Suggested Duration: </strong> {displayData.details.duration}</li>
              <li><strong>Address: </strong> {displayData.details.address}</li>
            </ul>
          </div>
        </div>
      </div>

      {/* TRIP PICKER MODAL */}
      {showPicker && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(20, 48, 74, 0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="info-card" style={{ width: '100%', maxWidth: '480px', margin: '20px' }}>
            <h2 style={{ textAlign: 'center', marginBottom: '24px' }}>Select Target Itinerary</h2>

            <div style={{ marginBottom: '20px' }}>
              <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '8px' }}>Trip Name</label>
              <select
                value={selectedTripId}
                onChange={(e) => {
                  setSelectedTripId(e.target.value);
                  setSelectedDay(1);
                }}
                style={{
                  width: '100%', padding: '12px', borderRadius: '12px',
                  border: '2px solid var(--line-soft)', outline: 'none', fontSize: '16px'
                }}
              >
                {eligibleTrips.map(trip => (
                  <option key={trip.id} value={trip.id}>{trip.title} ({trip.status})</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn-secondary" style={{ flex: 1 }} onClick={() => setShowPicker(false)}>Cancel</button>
              <button className="btn-primary" style={{ flex: 1 }} onClick={handleConfirmAdd}>Confirm Add</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AttractionPage;
