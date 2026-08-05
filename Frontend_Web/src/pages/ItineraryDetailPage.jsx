import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';

const ItineraryDetailPage = () => {
  const navigate = useNavigate();
  const { getActiveTrip, addDayToTrip, updateTripTitle } = useTrip();
  const trip = getActiveTrip();
  const [selectedDay, setSelectedDay] = useState(1);
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitleValue, setEditTitleValue] = useState('');

  useEffect(() => {
    if (trip) {
      setEditTitleValue(trip.title);
    }
  }, [trip]);

  if (!trip) return <div>Trip not found</div>;

  const handleAddDay = () => {
    addDayToTrip(trip.id);
  };

  const handleSaveTitle = () => {
    if (editTitleValue.trim()) {
      updateTripTitle(trip.id, editTitleValue.trim());
      setIsEditingTitle(false);
    }
  };

  const dayLocations = trip.locations.filter(loc => loc.day === selectedDay);

  return (
    <div className="route-page">
      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end'}}>
        <div style={{ flex: 1 }}>
          <button className="btn-secondary" style={{padding: '6px 16px', marginBottom: '12px', fontSize: '14px'}} onClick={() => navigate('/route')}>
            ← 返回列表
          </button>
          <div className="kicker" style={{color: 'var(--muted)', fontSize: '14px', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '2px'}}>My Trip Itinerary</div>

          <div className="title-area" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {isEditingTitle ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <input
                  type="text"
                  value={editTitleValue}
                  onChange={(e) => setEditTitleValue(e.target.value)}
                  style={{
                    fontSize: '32px', fontWeight: 'bold', border: '2px solid var(--jade)',
                    borderRadius: '8px', padding: '4px 12px', outline: 'none',
                    fontFamily: 'var(--display)'
                  }}
                  autoFocus
                  onBlur={handleSaveTitle}
                  onKeyDown={(e) => e.key === 'Enter' && handleSaveTitle()}
                />
              </div>
            ) : (
              <>
                <h1 style={{ margin: 0 }}>{trip.title}</h1>
                {trip.status !== 'FINISHED' && (
                  <button
                    onClick={() => setIsEditingTitle(true)}
                    style={{
                      background: 'none', border: 'none', cursor: 'pointer',
                      fontSize: '20px', color: 'var(--jade)', padding: '4px',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      opacity: 0.6, transition: 'opacity 0.2s'
                    }}
                    onMouseOver={(e) => e.currentTarget.style.opacity = '1'}
                    onMouseOut={(e) => e.currentTarget.style.opacity = '0.6'}
                    title="修改行程名称"
                  >
                    ✏️
                  </button>
                )}
              </>
            )}
          </div>
        </div>

        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          {trip.status !== 'FINISHED' ? (
            <>
              <button className="btn-secondary" onClick={() => navigate('/edit')}>编辑行程</button>
              <button className="btn-secondary" onClick={() => navigate('/route')}>保存</button>
              <button className="btn-primary">分享</button>
            </>
          ) : (
            <>
              <button className="btn-secondary" onClick={() => navigate('/map')}>在地图上查看</button>
              <button className="btn-primary">分享</button>
            </>
          )}
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
                <div className="stat-val">{trip.locations.length} 个</div>
              </div>
            </div>

            <div className="day-tabs" style={{marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '8px'}}>
              {Array.from({ length: trip.dayCount || 1 }).map((_, i) => (
                <div
                  key={i}
                  className={`day-tab ${selectedDay === i + 1 ? 'on' : ''}`}
                  onClick={() => setSelectedDay(i + 1)}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                >
                  <span>Day {i + 1}: {i === 0 ? '计划中' : '待规划'}</span>
                  {i === (trip.dayCount || 1) - 1 && trip.status !== 'FINISHED' && (
                    <span
                      onClick={(e) => { e.stopPropagation(); handleAddDay(); }}
                      style={{
                        background: 'var(--jade)', color: '#fff',
                        width: '24px', height: '24px', borderRadius: '50%',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '20px', fontWeight: '500', marginLeft: '10px',
                        cursor: 'pointer',
                        lineHeight: '0',
                        paddingBottom: '3px'
                      }}
                    >+</span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="itinerary-main">
          <div className="day-section">
            <div className="day-header">
              <span>{selectedDay}</span>
              Day {selectedDay}: {trip.date.split('-')[0].trim()}
            </div>

            <div className="timeline">
              {dayLocations.length === 0 ? (
                <div style={{padding: '40px', textAlign: 'center', background: '#fff', borderRadius: '20px', border: '1px dashed var(--line)'}}>
                  <p style={{color: 'var(--muted)'}}>该天暂无景点，去导入或添加一些吧！</p>
                  <button className="btn-primary" style={{marginTop: '20px'}} onClick={() => navigate('/import')}>导入游记</button>
                </div>
              ) : (
                dayLocations.map((item, idx) => (
                  <React.Fragment key={item.id}>
                    <div className="tl-node-row">
                      <div className="tl-left">
                        <div className="tl-circle">{idx + 1}</div>
                        {idx < dayLocations.length - 1 && <div className="tl-line"></div>}
                      </div>
                      <div className="tl-content">
                        <div className="tl-card-web">
                          <div className="tl-time">{item.time || '10:00'}</div>
                          <div className="tl-info">
                            <h3>{item.name}</h3>
                            <p>建议游玩 {item.duration || '1.5h'} · {idx === 0 ? '出发点' : '打卡点'}</p>
                          </div>
                          <div className="tl-actions">
                            <button onClick={() => navigate('/attraction')}>查看详情</button>
                          </div>
                        </div>
                      </div>
                    </div>

                    {idx < dayLocations.length - 1 && (
                      <div className="tl-transport">
                        <div className="tl-left"><div className="tl-line-dotted"></div></div>
                        <div className="tl-trans-info">
                          <span>{item.transport || '🚕 交通 15 分钟'}</span>
                        </div>
                      </div>
                    )}
                  </React.Fragment>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ItineraryDetailPage;
