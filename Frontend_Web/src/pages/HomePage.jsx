import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTrip } from '../context/TripContext';
import { apiFetch } from '../api';

const HomePage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { getActiveTrip } = useTrip();
  const [query, setQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const debounceRef = useRef(null);

  const activeTrip = getActiveTrip();

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    const q = query.trim();
    if (!q) {
      setSearchResults([]);
      setSearchError('');
      setSearching(false);
      return undefined;
    }

    // Destinations endpoint requires auth; skip API when logged out.
    if (!user) {
      setSearchResults([]);
      setSearchError('Log in to search destinations');
      return undefined;
    }

    setSearching(true);
    debounceRef.current = setTimeout(async () => {
      try {
        const data = await apiFetch(`/api/v1/destinations?q=${encodeURIComponent(q)}`);
        setSearchResults(data || []);
        setSearchError('');
      } catch (err) {
        setSearchResults([]);
        setSearchError(err.message || 'Search failed');
      } finally {
        setSearching(false);
      }
    }, 300);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query, user]);

  return (
    <div className="home-page">
      <section className="hero">
        <div className="container">
          <h1>Hello, where are you going?</h1>
          <p>Paste your travel brief, and LoomyTrip will help you build a smart itinerary.</p>
          <div className="hero-search-box" style={{ position: 'relative' }}>
            <div className="search-input">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              <input
                type="text"
                placeholder="Search destinations..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <button className="btn-primary" onClick={() => navigate(user ? '/import' : '/login')}>
              Smart Planning
            </button>

            {(searchResults.length > 0 || searching || searchError) && query.trim() && (
              <div className="search-dropdown" style={{
                position: 'absolute', top: '100%', left: 0, right: 0,
                background: '#fff', border: '1px solid var(--line)',
                borderRadius: '8px', marginTop: '8px', zIndex: 10,
                boxShadow: 'var(--shadow-sm)',
              }}>
                {searching && (
                  <div style={{ padding: '12px 16px', color: 'var(--muted)' }}>Searching...</div>
                )}
                {!searching && searchError && (
                  <div style={{ padding: '12px 16px', color: '#b42318' }}>{searchError}</div>
                )}
                {!searching && !searchError && searchResults.length === 0 && (
                  <div style={{ padding: '12px 16px', color: 'var(--muted)' }}>No destinations found</div>
                )}
                {searchResults.map((res, i) => (
                  <div
                    key={res.id}
                    className="search-result-item"
                    style={{
                      padding: '12px 16px',
                      borderBottom: i === searchResults.length - 1 ? 'none' : '1px solid var(--line-soft)',
                      cursor: 'pointer',
                    }}
                    onClick={() => {
                      setQuery(res.name);
                      navigate(`/attraction/${encodeURIComponent(res.name)}`);
                    }}
                  >
                    <div style={{ fontWeight: 600 }}>{res.name}</div>
                    {(res.address || res.category) && (
                      <div style={{ fontSize: '13px', color: 'var(--muted)', marginTop: 4 }}>
                        {[res.category, res.address].filter(Boolean).join(' · ')}
                      </div>
                    )}
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
                  backgroundImage: `linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.7)), url(${activeTrip.coverImage || 'https://images.unsplash.com/photo-1528181304800-2f1738b9cdc1?w=600&h=400&fit=crop'})`,
                }}
              >
                <h3>{activeTrip.title}</h3>
              </div>
              <div className="dest-info">
                <p>{activeTrip.dayCount} Days · {(activeTrip.locations || []).length} Attractions · {String(activeTrip.date).split(' - ')[0]}</p>
                <div className="progress-bar">
                  <div className="progress" style={{ width: `${(activeTrip.progress || 0) * 100}%` }}></div>
                </div>
                <a className="go-link">Continue Planning ➔</a>
              </div>
            </div>
          </div>
        </section>
      )}

      <section className="popular-destinations" style={{ marginTop: user ? '60px' : '20px' }}>
        <div className="section-title">
          <h2>Popular Destinations</h2>
        </div>
        <div className="destination-grid">
          {[
            { name: 'Bangkok', count: '8.2k', img: 'https://images.unsplash.com/photo-1552465011-b4e21bf6e79a' },
            { name: 'Phuket', count: '5.4k', img: 'https://images.unsplash.com/photo-1537996194471-e657df975ab4' },
            { name: 'Kyoto', count: '12.1k', img: 'https://images.unsplash.com/photo-1513415277900-a62401e19be4' },
          ].map((dest, i) => (
            <div key={i} className="destination-card" onClick={() => navigate(`/attraction/${encodeURIComponent(dest.name)}`)}>
              <div className="dest-img" style={{ backgroundImage: `linear-gradient(rgba(0,0,0,0),rgba(0,0,0,0.6)), url(${dest.img}?w=600&h=400&fit=crop)` }}>
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
