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

  const handleSavePreferences = async () => {
    try {
      await updatePreferences(travelStyle, preferTransport);
      setShowSettings(false);
      alert('Preferences saved');
    } catch (error) {
      alert(error.response?.data?.message || 'Failed to save preferences, please try again');
    }
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
            {(user.username || user.email || 'T').charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 style={{ fontSize: '32px', marginBottom: '8px' }}>{user.username || user.email}</h1>
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
          <div className="info-card" style={{textAlign: 'center', padding: '24px', cursor: 'pointer'}} onClick={() => navigate('/route')}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>{trips.length}</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Trips</div>
          </div>
          <div className="info-card" style={{textAlign: 'center', padding: '24px', cursor: 'pointer'}} onClick={() => handleMenuClick('Favorites')}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>45</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Favorites</div>
          </div>
          <div className="info-card" style={{textAlign: 'center', padding: '24px', cursor: 'pointer'}} onClick={() => handleMenuClick('Footprints')}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>8</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Footprints</div>
          </div>
        </div>

        {showSettings ? (
          <div className="info-card" style={{ padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '24px' }}>⚙️ Info</h2>
              <button className="btn-secondary" onClick={() => setShowSettings(false)}>Close</button>
            </div>
            <p style={{ color: 'var(--muted)' }}>Global account preferences are not supported by the current database schema.</p>
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
