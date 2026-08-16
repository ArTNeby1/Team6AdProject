import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAdminAuth } from './AdminAuthContext';

// Console shell: fixed sidebar nav + top bar, content rendered via <Outlet/>.
// New admin sections are added by dropping a NavLink here and a nested route.
const NAV = [
  { to: '/admin', end: true, label: 'Dashboard', icon: '▚' },
  { to: '/admin/users', end: false, label: 'Users', icon: '☰' },
  { to: '/admin/eval', end: false, label: 'LLM Evaluation', icon: '◈' },
];

export default function AdminLayout() {
  const { admin, logout } = useAdminAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/admin/login', { replace: true });
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <div className="admin-brand">
          <span className="admin-brand-mark">L</span>
          <span>LoomyTrip <em>Admin</em></span>
        </div>
        <nav className="admin-nav">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `admin-nav-link${isActive ? ' is-active' : ''}`}
            >
              <span className="admin-nav-icon" aria-hidden>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-spacer" />
          <div className="admin-account">
            <span className="admin-account-email">{admin?.email}</span>
            <span className={`admin-role-badge admin-role-${admin?.role}`}>{admin?.role}</span>
            <button className="admin-btn admin-btn-ghost" onClick={handleLogout}>Sign out</button>
          </div>
        </header>
        <main className="admin-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
