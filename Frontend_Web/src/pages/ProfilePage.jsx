import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTrip } from '../context/TripContext';

const ProfilePage = () => {
  const { user, logout, updatePreferences } = useAuth();
  const { trips } = useTrip();
  const navigate = useNavigate();

  const [showSettings, setShowSettings] = useState(false);
  const [travelStyle, setTravelStyle] = useState(user?.travelStyle || 'Cultural');
  const [preferTransport, setPreferTransport] = useState(user?.preferTransport || 'Public');

  if (!user) return null;

  const handleLogout = () => {
    if (window.confirm('Are you sure you want to log out?')) {
      logout();
      navigate('/');
    }
  };

  const handleSavePreferences = () => {
    updatePreferences(travelStyle, preferTransport);
    setShowSettings(false);
    alert('Preferences saved');
  };

  const handleMenuClick = (title) => {
    if (title === 'Preferences') {
      setShowSettings(true);
    } else {
      alert(`Feature in development: ${title}`);
    }
  };

  return (
    <div className="profile-page" style={{ padding: '40px 0', minHeight: '70vh' }}>
      <div className="container" style={{maxWidth: '800px'}}>
        <header className="page-header" style={{display: 'flex', alignItems: 'center', gap: '32px', marginBottom: '48px'}}>
          <div className="profile-avatar" style={{
            width: '120px', height: '100px', borderRadius: '40px',
            background: 'linear-gradient(135deg, var(--jade), var(--jade-deep))',
            color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '48px', fontWeight: '900',
            boxShadow: 'var(--shadow)'
          }}>
            {user.username.charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 style={{ fontSize: '32px', marginBottom: '8px' }}>{user.username}</h1>
            <p style={{color: 'var(--muted)', fontSize: '16px'}}>
               ID: {user.email} | <span style={{ color: 'var(--jade-deep)', fontWeight: '600' }}>Travel Expert</span>
            </p>
            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
              <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                {user.gender === 'Male' ? '♂ Male' : user.gender === 'Female' ? '♀ Female' : 'Other'}
              </span>
              <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                {user.age} years old
              </span>
            </div>
          </div>
        </header>

        <div className="profile-stats-grid" style={{
          display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px',
          marginBottom: '48px'
        }}>
          <div className="info-card" style={{textAlign: 'center', padding: '24px'}}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>{trips.length}</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Trips</div>
          </div>
          <div className="info-card" style={{textAlign: 'center', padding: '24px'}}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>45</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Favorites</div>
          </div>
          <div className="info-card" style={{textAlign: 'center', padding: '24px'}}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>8</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Footprints</div>
          </div>
        </div>

        {showSettings ? (
          <div className="info-card" style={{ padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '24px' }}>⚙️ Preferences</h2>
              <button className="btn-secondary" onClick={() => setShowSettings(false)}>Cancel</button>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '32px' }}>
              <div>
                <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '12px' }}>Default Travel Style</label>
                <select
                  value={travelStyle}
                  onChange={(e) => setTravelStyle(e.target.value)}
                  style={{ width: '100%', padding: '14px', borderRadius: '14px', border: '2px solid var(--line-soft)', outline: 'none', fontSize: '16px' }}
                >
                  <option value="Cultural">Cultural Depth</option>
                  <option value="Leisure">Leisure Vacation</option>
                  <option value="Adventure">Outdoor Adventure</option>
                  <option value="Foodie">Gourmet Tasting</option>
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '12px' }}>Default Transport Mode</label>
                <select
                  value={preferTransport}
                  onChange={(e) => setPreferTransport(e.target.value)}
                  style={{ width: '100%', padding: '14px', borderRadius: '14px', border: '2px solid var(--line-soft)', outline: 'none', fontSize: '16px' }}
                >
                  <option value="Public">Public Transport</option>
                  <option value="Taxi">Taxi/Charter</option>
                  <option value="Walking">Walking/Cycling</option>
                </select>
              </div>
            </div>
            <button className="btn-primary" style={{ width: '100%', padding: '16px' }} onClick={handleSavePreferences}>Save Preferences</button>
          </div>
        ) : (
          <div className="info-card" style={{padding: '8px'}}>
            <div className="menu-item" style={menuItemStyle} onClick={() => handleMenuClick('My Favorites')}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>⭐</span> My Favorites
              </span>
              <span style={{color: 'var(--line)', fontSize: '18px'}}>➔</span>
            </div>
            <div className="menu-item" style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)'}} onClick={() => handleMenuClick('Reviewed Attractions')}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>📝</span> Reviewed Attractions
              </span>
              <span style={{color: 'var(--line)', fontSize: '18px'}}>➔</span>
            </div>
            <div className="menu-item" style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)'}} onClick={() => handleMenuClick('Preferences')}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>⚙️</span> Preferences
              </span>
              <span style={{color: 'var(--line)', fontSize: '18px'}}>➔</span>
            </div>
            <div
              className="menu-item logout-btn"
              style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)', color: 'var(--coral)'}}
              onClick={handleLogout}
            >
              <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>🚪</span> Logout
              </span>
            </div>
          </div>
        )}

        <div style={{ marginTop: '32px', textAlign: 'center' }}>
           <p style={{ color: 'var(--line)', fontSize: '13px' }}>LoomyTrip v1.0.0 Pro</p>
        </div>
      </div>
    </div>
  );
};

const menuItemStyle = {
  padding: '18px 24px',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  cursor: 'pointer',
  fontSize: '16px',
  fontWeight: '600',
  transition: 'background 0.2s',
  borderRadius: '16px'
};

export default ProfilePage;
