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
          <h1>清迈 3 日深度文化之旅</h1>
        </div>
        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          <button className="btn-secondary" onClick={() => navigate('/edit')}>编辑行程</button>
          <button className="btn-primary">保存并分享</button>
        </div>
      </header>

      <div className="route-grid">
        <div className="itinerary-sidebar">
          <div className="info-card">
            <h3>行程概览</h3>
            <div className="aibadge" style={{background: '#FCEFD6', border: '1px solid #F3DDAF', padding: '12px', borderRadius: '12px', margin: '16px 0', fontSize: '14px'}}>
              <span className="s">🪄</span>
              <span className="t" style={{color: '#8a5a10', fontWeight: 700}}> AI 已根据偏好优化路线</span>
            </div>
            <div className="route-stats" style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px'}}>
              <div className="stat-box">
                <div className="stat-label">总里程</div>
                <div className="stat-val">15.4km</div>
              </div>
              <div className="stat-box">
                <div className="stat-label">地点总数</div>
                <div className="stat-val">{itinerary.length} 个</div>
              </div>
            </div>
            <div className="day-tabs" style={{marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '8px'}}>
              <div className="day-tab on">Day 1: 古城经典</div>
              <div className="day-tab">Day 2: 宁曼时光</div>
              <div className="day-tab">Day 3: 丛林探险</div>
            </div>
          </div>
        </div>

        <div className="itinerary-main">
          <div className="day-section">
            <div className="day-header">
              <span>1</span>
              Day 1: 2024年10月24日 · 清迈古城
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
                          <p>建议游玩 {item.duration || '1.5h'} · {idx === 0 ? '必去景点' : '推荐'}</p>
                        </div>
                        <div className="tl-actions">
                          <button onClick={() => navigate('/attraction')}>查看详情</button>
                        </div>
                      </div>
                    </div>
                  </div>

                  {idx < itinerary.length - 1 && (
                    <div className="tl-transport">
                      <div className="tl-left"><div className="tl-line-dotted"></div></div>
                      <div className="tl-trans-info">
                        <span>{item.transport || '🚕 交通 15 分钟'}</span>
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
