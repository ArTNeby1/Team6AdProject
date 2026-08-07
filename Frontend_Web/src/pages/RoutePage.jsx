import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const RoutePage = () => {
  const navigate = useNavigate();
  const { itinerary } = useTrip();

  return (
    <div className="route-page">
      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end'}}>
        <div>
          <div className="kicker" style={{color: 'var(--muted)', fontSize: '14px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '2px'}}>My Trip Itinerary</div>
          <h1>Chiang Mai 3 Days Deep Culture Trip</h1>
        </div>
        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          <button className="btn-secondary" onClick={() => navigate('/edit')}>Edit Itinerary</button>
          <button className="btn-primary">Save and Share</button>
        </div>
      </header>

      <div className="route-grid">
        <div className="itinerary-sidebar">
          <div className="info-card">
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
                <div className="stat-val">{itinerary.length} sites</div>
              </div>
            </div>
            <div className="day-tabs" style={{marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '8px'}}>
              <div className="day-tab on">Day 1: Old City Classics</div>
              <div className="day-tab">Day 2: Nimman Time</div>
              <div className="day-tab">Day 3: Jungle Adventure</div>
            </div>
          </div>
        </div>

        <div className="itinerary-main">
          <div className="day-section">
            <div className="day-header">
              <span>1</span>
              Day 1: Oct 24, 2024 · Chiang Mai Old City
            </div>

            <div className="timeline">
              {itinerary.map((item, idx) => (
                <React.Fragment key={item.id}>
                  <div className="tl-node-row">
                    <div className="tl-left">
                      <div className="tl-circle">{idx + 1}</div>
                      {idx < itinerary.length - 1 && <div className="tl-line"></div>}
                    </div>
                    <div className="tl-content">
                      <div className="tl-card-web">
                        <div className="tl-time">{item.time || '10:00'}</div>
                        <div className="tl-info">
                          <h3>{item.name}</h3>
                          <p>Suggested Duration {item.duration || '1.5h'} · {idx === 0 ? 'Must-visit' : 'Recommended'}</p>
                        </div>
                        <div className="tl-actions">
                          <button onClick={() => navigate('/attraction')}>View Details</button>
                        </div>
                      </div>
                    </div>
                  </div>

                  {idx < itinerary.length - 1 && (
                    <div className="tl-transport">
                      <div className="tl-left"><div className="tl-line-dotted"></div></div>
                      <div className="tl-trans-info">
                        <span>{item.transport || '🚕 15 min Transport'}</span>
                      </div>
                    </div>
                  )}
                </React.Fragment>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RoutePage;
