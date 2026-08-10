import React, { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';

const MOCK_ATTRACTIONS = {
  'Wat Chedi Luang': {
    name: 'Wat Chedi Luang',
    enName: 'Wat Chedi Luang Varavihara',
    rating: '4.8',
    reviews: '4.2k',
    tags: ['Ancient Temple', 'Lanna Style', 'Chiang Mai Old City'],
    desc: 'Wat Chedi Luang, also known as the Temple of the Big Stupa, was built in 1411 and is the most famous of the six major temples in Chiang Mai. The large stupa inside was damaged in an earthquake in 1545 but remains magnificent and is a landmark of the old city.',
    img: 'https://images.unsplash.com/photo-1590059367468-61d0f5f725a3?w=1200&h=600&fit=crop',
    details: {
      time: '08:00 - 17:00',
      price: '40 THB',
      duration: '1.5 - 2 hours',
      address: '103 Road King Prajadhipok Phra Sing, Chiang Mai'
    }
  },
  'Tha Phae Gate': {
    name: 'Tha Phae Gate',
    enName: 'Tha Phae Gate',
    rating: '4.6',
    reviews: '3.5k',
    tags: ['Landmark', 'Ancient City Wall', 'Popular Spot'],
    desc: 'The east gate of the Chiang Mai old city is the most complete surviving city gate. There are often flocks of pigeons in front of the red brick wall, which is a popular spot for tourists to take photos and also the starting point of the Sunday Night Market.',
    img: 'https://images.unsplash.com/photo-1583251633146-d0c6c3300f72?w=1200&h=600&fit=crop',
    details: {
      time: 'Open All Day',
      price: 'Free',
      duration: '0.5 - 1 hour',
      address: 'Tha Phae Road, Chiang Mai'
    }
  },
  'Nimman Road': {
    name: 'Nimman Road',
    enName: 'Nimman Road',
    rating: '4.5',
    reviews: '2.8k',
    tags: ['Shopping', 'Cafe', 'Trendy Community'],
    desc: 'The trendiest street in Chiang Mai, gathering many art and design shops, cafes, bars, and restaurants. Maya Department Store and One Nimman are both located here.',
    img: 'https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=1200&h=600&fit=crop',
    details: {
      time: 'Open All Day',
      price: 'Free',
      duration: '2 - 4 hours',
      address: 'Nimmanhaemin Road, Chiang Mai'
    }
  },
  'Sunday Night Market': {
    name: 'Sunday Night Market',
    enName: 'Sunday Night Market',
    rating: '4.9',
    reviews: '10k+',
    tags: ['Shopping', 'Food', 'Local Culture'],
    desc: "Chiang Mai's largest night market, held only on Sunday nights. Stretching from Tha Phae Gate to Wat Phra Singh, it is filled with handicrafts, clothing, and food stalls.",
    img: 'https://images.unsplash.com/photo-1582650843132-e03403260742?w=1200&h=600&fit=crop',
    details: {
      time: 'Sunday 17:00 - 23:00',
      price: 'Free',
      duration: '2 - 4 hours',
      address: 'Ratchadamnoen Road, Chiang Mai'
    }
  }
};

const AttractionPage = () => {
  const { name } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { trips, addLocationsToTripDay } = useTrip();

  const [showPicker, setShowPicker] = useState(false);
  const [selectedTripId, setSelectedTripId] = useState('');
  const [selectedDay, setSelectedDay] = useState(1);

  const attractionName = name ? decodeURIComponent(name) : 'Wat Chedi Luang';
  const attraction = MOCK_ATTRACTIONS[attractionName];

  const data = useMemo(() => {
    if (attraction) return attraction;
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
        address: 'Chiang Mai'
      }
    };
  }, [attractionName, attraction]);

  // Filter trips that can be edited (Active or Not Started)
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

  const handleConfirmAdd = () => {
    if (!selectedTripId) return;
    addLocationsToTripDay(selectedTripId, selectedDay, [data.name]);
    setShowPicker(false);
    if (window.confirm(`Successfully added ${data.name} to the itinerary. View it now?`)) {
      navigate(`/itinerary/${selectedTripId}`);
    }
  };

  const selectedTrip = trips.find(t => t.id === selectedTripId);

  return (
    <div className="attraction-page">
      <div
        className="attraction-hero"
        style={{
          backgroundImage: data.img ? `url(${data.img})` : 'none',
          backgroundColor: data.placeholderColor || 'transparent'
        }}
      >
        {!data.img && <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '100px', opacity: 0.1, color: '#fff', fontWeight: 'bold'}}>LoomyTrip</div>}
        <div className="hero-overlay"></div>
        <div className="hero-content">
          <div className="container">
            <h1>{data.name}</h1>
            <p>{data.enName}</p>
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
              <span className="chip star">★ {data.rating} ({data.reviews} Reviews)</span>
              {data.tags.map((tag, i) => (
                <span key={i} className="chip">{tag}</span>
              ))}
            </div>
            <p className="desc-text">{data.desc}</p>
          </div>

          <div className="info-card">
            <h2>Tourist Reviews</h2>
            <div className="comments-list">
              <div className="cmt-item">
                <div className="cmt-avatar" style={{background: '#EF6E5B'}}>W</div>
                <div className="cmt-body">
                  <div className="cmt-meta">
                    <strong>Lily Wang</strong>
                    <span className="cmt-stars">★★★★★</span>
                  </div>
                  <p className="cmt-text">The atmosphere here is great, {data.name} has a lot of local character, highly recommended to watch the sunset here.</p>
                </div>
              </div>
            </div>
            <button className="btn-outline" style={{width: '100%', marginTop: '20px'}}>＋ Write a Review</button>
          </div>
        </div>

        <div className="attraction-sidebar">
          <div className="info-card">
            <h3>Practical Information</h3>
            <ul className="details-list">
              <li><strong>Opening Hours: </strong> {data.details.time}</li>
              <li><strong>Ticket Price: </strong> {data.details.price}</li>
              <li><strong>Suggested Duration: </strong> {data.details.duration}</li>
              <li><strong>Address: </strong> {data.details.address}</li>
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
                  <option key={trip.id} value={trip.id}>{trip.title} ({trip.status === 'ACTIVE' ? 'Active' : 'Upcoming'})</option>
                ))}
              </select>
            </div>

            {selectedTrip && (
              <div style={{ marginBottom: '32px' }}>
                <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '8px' }}>Select Day</label>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                  {Array.from({ length: selectedTrip.dayCount || 1 }).map((_, i) => (
                    <button
                      key={i}
                      onClick={() => setSelectedDay(i + 1)}
                      style={{
                        padding: '10px 20px', borderRadius: '10px', border: 'none',
                        background: selectedDay === i + 1 ? 'var(--ink)' : 'var(--mint)',
                        color: selectedDay === i + 1 ? '#fff' : 'var(--jade-deep)',
                        fontWeight: 'bold', cursor: 'pointer'
                      }}
                    >
                      Day {i + 1}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                className="btn-secondary"
                style={{ flex: 1 }}
                onClick={() => setShowPicker(false)}
              >Cancel</button>
              <button
                className="btn-primary"
                style={{ flex: 1 }}
                onClick={handleConfirmAdd}
              >Confirm Add</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AttractionPage;
