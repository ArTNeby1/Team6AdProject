import React, { useEffect, useState } from 'react';
import { useAdminAuth } from '../AdminAuthContext';
import { apiFetch } from '../api';

// Console landing: real platform metrics pulled from the admin API.
// (Replaces the S0 placeholder cards.)
export default function AdminDashboardPage() {
  const { admin } = useAdminAuth();
  const [totalUsers, setTotalUsers] = useState(null); // null = still loading
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    // size=1 keeps the payload tiny — we only need the total count.
    apiFetch('/api/v1/admin/users?page=0&size=1')
      .then((data) => { if (active) setTotalUsers(data.totalElements ?? 0); })
      .catch((err) => { if (active) setError(err.message || 'Failed to load metrics.'); });
    return () => { active = false; };
  }, []);

  return (
    <div>
      <div className="admin-page-head">
        <h1>Dashboard</h1>
        <p className="admin-page-sub">Platform overview</p>
      </div>

      {error && <div className="admin-alert" role="alert">{error}</div>}

      <div className="admin-card-grid">
        <div className="admin-stat-card">
          <span className="admin-stat-label">Total users</span>
          <span className="admin-stat-value">{totalUsers === null ? '—' : totalUsers}</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">Your role</span>
          <span className="admin-stat-value">{admin?.role}</span>
        </div>
      </div>
    </div>
  );
}
