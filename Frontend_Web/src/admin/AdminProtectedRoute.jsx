import React from 'react';
import PropTypes from 'prop-types';
import { Navigate, useLocation } from 'react-router-dom';
import { useAdminAuth } from './AdminAuthContext';

// Gates the admin console: unauthenticated visitors are bounced to the admin
// login, preserving where they were headed so login can send them back.
export default function AdminProtectedRoute({ children }) {
  const { admin, loading } = useAdminAuth();
  const location = useLocation();

  if (loading) {
    return <div className="admin-loading">Loading…</div>;
  }
  if (!admin) {
    return <Navigate to="/admin/login" state={{ from: location }} replace />;
  }
  return children;
}

AdminProtectedRoute.propTypes = {
  children: PropTypes.node.isRequired,
};
