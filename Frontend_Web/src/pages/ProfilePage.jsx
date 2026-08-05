import React from 'react';

const ProfilePage = () => {
  return (
    <div className="profile-page">
      <div className="container" style={{maxWidth: '800px'}}>
        <header className="page-header" style={{display: 'flex', alignItems: 'center', gap: '24px', marginBottom: '40px'}}>
          <div className="profile-avatar" style={{
            width: '100px', height: '100px', borderRadius: '50%',
            background: 'var(--jade)', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '36px', fontWeight: 'bold'
          }}>
            L
          </div>
          <div>
            <h1>林小舟</h1>
            <p style={{color: 'var(--muted)', marginTop: '4px'}}>已加入沿途 128 天 | 旅行爱好者</p>
          </div>
        </header>

        <div className="profile-stats-grid" style={{
          display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px',
          marginBottom: '40px'
        }}>
          <div className="info-card" style={{textAlign: 'center'}}>
            <div style={{fontSize: '24px', fontWeight: 'bold'}}>12</div>
            <div style={{fontSize: '14px', color: 'var(--muted)'}}>行程</div>
          </div>
          <div className="info-card" style={{textAlign: 'center'}}>
            <div style={{fontSize: '24px', fontWeight: 'bold'}}>45</div>
            <div style={{fontSize: '14px', color: 'var(--muted)'}}>收藏</div>
          </div>
          <div className="info-card" style={{textAlign: 'center'}}>
            <div style={{fontSize: '24px', fontWeight: 'bold'}}>8</div>
            <div style={{fontSize: '14px', color: 'var(--muted)'}}>足迹</div>
          </div>
        </div>

        <div className="info-card" style={{padding: '0'}}>
          <div className="menu-item" style={menuItemStyle}>
            <span>⭐ 我的收藏</span>
            <span style={{color: 'var(--line)'}}>➔</span>
          </div>
          <div className="menu-item" style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)'}}>
            <span>📝 评价过的景点</span>
            <span style={{color: 'var(--line)'}}>➔</span>
          </div>
          <div className="menu-item" style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)'}}>
            <span>⚙️ 偏好设置</span>
            <span style={{color: 'var(--line)'}}>➔</span>
          </div>
          <div className="menu-item" style={{...menuItemStyle, borderTop: '1px solid var(--line-soft)', color: 'var(--coral)'}}>
            <span>🚪 退出登录</span>
          </div>
        </div>
      </div>
    </div>
  );
};

const menuItemStyle = {
  padding: '16px 20px',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  cursor: 'pointer',
  fontSize: '15px',
  fontWeight: '500'
};

export default ProfilePage;
