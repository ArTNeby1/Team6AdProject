import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Header = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  return (
    <header className="web-header">
      <div className="header-container">
        <Link to="/" className="web-brand">
          <span className="cn">沿途</span>
          <span className="en">Yántú</span>
        </Link>
        <nav className="web-nav">
          <Link to="/" className={location.pathname === '/' ? 'active' : ''}>首页</Link>
          <Link to="/import" className={location.pathname === '/import' ? 'active' : ''}>导入行程</Link>
          <Link to="/route" className={location.pathname === '/route' ? 'active' : ''}>我的路线</Link>
          <Link to="/map" className={location.pathname === '/map' ? 'active' : ''}>探索地图</Link>
          <Link to="/profile" className={location.pathname === '/profile' ? 'active' : ''}>我的</Link>
        </nav>
        <div className="header-actions" style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          {user ? (
            <>
              <div className="user-info" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: 'var(--jade)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '14px' }}>
                  {user.username.charAt(0)}
                </div>
                <span style={{ fontWeight: '600', color: 'var(--ink)' }}>{user.username}</span>
              </div>
              <button className="btn-secondary" style={{ padding: '6px 16px', fontSize: '13px' }} onClick={logout}>退出</button>
              <button className="btn-primary" onClick={() => navigate('/import')}>开始规划</button>
            </>
          ) : (
            <>
              <Link to="/login" style={{ fontWeight: '600', color: 'var(--muted)' }}>登录</Link>
              <button className="btn-primary" onClick={() => navigate('/login')}>开始规划</button>
            </>
          )}
        </div>
      </div>
    </header>
  );
};

export default Header;
