import React from 'react';
import { useAdminAuth } from '../AdminAuthContext';

// Placeholder landing for the console skeleton (S0). Real metrics land later;
// for now it confirms the shell + auth work and points to the built section.
export default function AdminDashboardPage() {
  const { admin } = useAdminAuth();

  return (
    <div>
      <div className="admin-page-head">
        <h1>Dashboard</h1>
        <p className="admin-page-sub">Signed in as {admin?.email}</p>
      </div>

      <div className="admin-card-grid">
        <div className="admin-stat-card">
          <span className="admin-stat-label">Your role</span>
          <span className="admin-stat-value">{admin?.role}</span>
        </div>
        <div className="admin-stat-card admin-stat-card--muted">
          <span className="admin-stat-label">Sections live</span>
          <span className="admin-stat-value">Users</span>
        </div>
        <div className="admin-stat-card admin-stat-card--muted">
          <span className="admin-stat-label">Coming next</span>
          <span className="admin-stat-value">Imports · Content</span>
        </div>
      </div>
    </div>
  );
}
