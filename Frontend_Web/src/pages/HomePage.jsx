import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTrip } from '../context/TripContext';

const HomePage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { getActiveTrip } = useTrip();
  const [query, setQuery] = useState('');

  const handleQueryChange = (e) => {
    const element = e.target;
    setQuery(element.value);

    // Auto-expand logic
    element.style.height = '24px'; // Reset to min height to recalculate
    const newHeight = Math.min(element.scrollHeight, 200);
    element.style.height = `${newHeight}px`;
  };

  const activeTrip = getActiveTrip();

  const allPlaces = ["Marina Bay Sands, Singapore", "Sentosa Island", "Chinatown", "Little India", "Orchard Road", "Universal Studios Singapore", "Chiang Mai Old City", "Wat Chedi Luang", "Doi Suthep", "Nimman Road"];
  const searchResults = allPlaces.filter(p => p.toLowerCase().includes(query.toLowerCase()) && query !== '');

  return (
    <div className="home-page">
      <section className="hero">
        <div className="container">
          <h1>Hello, where are you going?</h1>
          <p>Paste your travel log link here, and LoomyTrip AI will automatically generate a smart itinerary for you.</p>
          <div className="hero-search-box">
            <div className="search-input-wrapper">
              <svg
                width="20" height="20" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"
                strokeLinejoin="round"
              >
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              <textarea
                rows="1"
                placeholder="Search for destinations, travel logs, or attractions..."
                value={query}
                onChange={handleQueryChange}
              />
            </div>
            <button className="btn-primary" onClick={() => navigate(user ? `/import?text=${encodeURIComponent(query)}` : '/login')}>
              Smart Planning
            </button>

            {searchResults.length > 0 && (
              <div className="search-dropdown" style={{
                position: 'absolute', top: '100%', left: 0, right: 0,
                background: '#fff', border: '1px solid var(--line)',
                borderRadius: '8px', marginTop: '8px', zIndex: 10,
                boxShadow: 'var(--shadow-sm)'
              }}>
                {searchResults.map((res, i) => (
                  <div key={i} className="search-result-item" style={{
                    padding: '12px 16px', borderBottom: i === searchResults.length - 1 ? 'none' : '1px solid var(--line-soft)',
                    cursor: 'pointer'
                  }} onClick={() => { setQuery(res); navigate(`/attraction/${encodeURIComponent(res)}`); }}>
                    {res}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </section>

      {user && activeTrip && (
        <section className="featured-trips">
          <div className="section-title">
            <h2>Active Itinerary</h2>
            <a href="#" className="link-more" onClick={(e) => { e.preventDefault(); navigate('/route'); }}>View All</a>
          </div>
          <div className="destination-grid">
            <div className="destination-card" onClick={() => navigate(`/itinerary/${activeTrip.id}`)}>
              <div
                className="dest-img"
                style={{
                  backgroundImage: `linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.7)), url(${activeTrip.coverImage || 'https://images.unsplash.com/photo-1528181304800-2f1738b9cdc1?w=600&h=400&fit=crop'})`
                }}
              >
                <h3>{activeTrip.title}</h3>
              </div>
              <div className="dest-info">
                <p>{activeTrip.dayCount} Days · {activeTrip.locations.length} Attractions · {activeTrip.date.split(' - ')[0]}</p>
                <div className="progress-bar">
                  <div className="progress" style={{ width: `${(activeTrip.progress || 0) * 100}%` }}></div>
                </div>
                <a className="go-link">Continue Planning ➔</a>
              </div>
            </div>
          </div>
        </section>
      )}

      <section className="popular-destinations" style={{marginTop: user ? '60px' : '20px'}}>
        <div className="section-title">
          <h2>Popular Destinations</h2>
        </div>
        <div className="destination-grid">
          {[
            { name: "Bangkok", count: "8.2k", img: "https://images.unsplash.com/photo-1552465011-b4e21bf6e79a" },
            { name: "Phuket", count: "5.4k", img: "https://images.unsplash.com/photo-1537996194471-e657df975ab4" },
            { name: "Kyoto", count: "12.1k", img: "https://images.unsplash.com/photo-1513415277900-a62401e19be4" }
          ].map((dest, i) => (
            <div key={i} className="destination-card" onClick={() => navigate(`/attraction/${encodeURIComponent(dest.name)}`)}>
              <div className="dest-img" style={{backgroundImage: `linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.6)), url(${dest.img}?w=600&h=400&fit=crop)`}}>
                <h3>{dest.name}</h3>
              </div>
              <div className="dest-info">
                <p>{dest.count} Saved</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};

export default HomePage;
