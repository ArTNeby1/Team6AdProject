import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

const Header = () => {
  const location = useLocation();
  const navigate = useNavigate();

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
        <div className="header-actions">
          <button className="btn-primary" onClick={() => navigate('/import')}>开始规划</button>
        </div>
      </div>
    </header>
  );
};

export default Header;
