import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTrip } from '../context/TripContext';
import api from '../services/api';

const ProfilePage = () => {
  const { user, logout, updateProfile } = useAuth();
  const { trips } = useTrip();
  const navigate = useNavigate();

  const [profileData, setProfileData] = useState(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editUsername, setEditUsername] = useState('');
  const [editAge, setEditAge] = useState('');
  const [editGender, setEditGender] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const response = await api.get('/users/me');
        setProfileData(response.data);
      } catch (error) {
        console.error('Failed to fetch profile:', error);
      }
    };
    fetchProfile();
  }, []);

  if (!user) return null;

  const handleLogout = () => {
    if (window.confirm('Are you sure you want to log out?')) {
      logout();
      navigate('/');
    }
  };

  const handleStartEditing = () => {
    setEditUsername(profileData?.username || user.username || '');
    setEditAge(profileData?.age != null ? String(profileData.age) : '');
    setEditGender(profileData?.gender || '');
    setSaveError('');
    setIsEditing(true);
  };

  const handleCancelEditing = () => {
    setIsEditing(false);
    setSaveError('');
  };

  const handleSaveProfile = async () => {
    if (!editUsername.trim()) {
      setSaveError('Username cannot be blank');
      return;
    }
    setSaving(true);
    setSaveError('');
    try {
      const updated = await updateProfile(
        editUsername.trim(),
        editAge === '' ? null : Number(editAge),
        editGender || null
      );
      setProfileData(prev => ({ ...prev, username: updated.username, age: updated.age, gender: updated.gender }));
      setIsEditing(false);
    } catch (error) {
      setSaveError(error.response?.data?.message || 'Failed to save, please try again');
    } finally {
      setSaving(false);
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
            {(profileData?.username || user.email || 'T').charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 style={{ fontSize: '32px', marginBottom: '8px' }}>{profileData?.username || user.email.split('@')[0]}</h1>
            <p style={{color: 'var(--muted)', fontSize: '16px'}}>
               ID: {user.email} | <span style={{ color: 'var(--jade-deep)', fontWeight: '600' }}>Travel Expert</span>
            </p>
            <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
              <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                {profileData?.gender || 'Other'}
              </span>
              <span style={{ padding: '4px 12px', background: 'var(--mint)', borderRadius: '8px', fontSize: '13px', color: 'var(--jade-deep)', fontWeight: 'bold' }}>
                {profileData?.age ? `${profileData.age} years old` : 'Age N/A'}
              </span>
            </div>
          </div>
        </header>

        <div className="profile-stats-grid" style={{
          display: 'flex', justifyContent: 'center',
          marginBottom: '48px'
        }}>
          <div className="info-card" style={{textAlign: 'center', padding: '24px', cursor: 'pointer', minWidth: '200px'}} onClick={() => navigate('/route')}>
            <div style={{fontSize: '32px', fontWeight: '900', color: 'var(--ink)'}}>{trips.length}</div>
            <div style={{fontSize: '14px', color: 'var(--muted)', marginTop: '4px', fontWeight: '600'}}>Trips</div>
          </div>
        </div>

        {isEditing ? (
          <div className="info-card" style={{ padding: '32px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <h2 style={{ fontSize: '24px' }}>✏️ Edit Profile</h2>
              <button className="btn-secondary" onClick={handleCancelEditing} disabled={saving}>Cancel</button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '360px' }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontWeight: '600', fontSize: '14px', color: 'var(--muted)' }}>
                Username
                <input
                  type="text"
                  value={editUsername}
                  onChange={e => setEditUsername(e.target.value)}
                  style={inputStyle}
                  maxLength={64}
                />
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontWeight: '600', fontSize: '14px', color: 'var(--muted)' }}>
                Age
                <input
                  type="number"
                  min="0"
                  max="150"
                  value={editAge}
                  onChange={e => setEditAge(e.target.value)}
                  style={inputStyle}
                  placeholder="Not set"
                />
              </label>

              <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontWeight: '600', fontSize: '14px', color: 'var(--muted)' }}>
                Gender
                <select value={editGender} onChange={e => setEditGender(e.target.value)} style={inputStyle}>
                  <option value="">Not set</option>
                  <option value="Male">Male</option>
                  <option value="Female">Female</option>
                  <option value="Other">Other</option>
                </select>
              </label>

              {saveError && <p style={{ color: 'var(--coral)', fontSize: '14px', margin: 0 }}>{saveError}</p>}

              <button className="btn-primary" onClick={handleSaveProfile} disabled={saving}>
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>
        ) : (
          <div className="info-card" style={{padding: '8px'}}>
            <div className="menu-item" style={menuItemStyle} onClick={handleStartEditing}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontSize: '20px' }}>✏️</span> Edit Profile
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

const inputStyle = {
  padding: '12px 14px',
  borderRadius: '12px',
  border: '1px solid var(--line)',
  fontSize: '15px',
  fontFamily: 'inherit',
  color: 'var(--ink)',
  background: 'var(--surface)'
};

export default ProfilePage;
