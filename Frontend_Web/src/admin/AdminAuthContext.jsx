import React, { createContext, useContext, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { apiFetch, ADMIN_TOKEN_KEY, getAdminToken } from './api';

// Separate auth context from the traveler-facing AuthContext: the admin console
// is a different principal with a different token, stored under its own key.
const AdminAuthContext = createContext(null);

const ADMIN_INFO_KEY = 'loomytrip_admin_info';

export const useAdminAuth = () => useContext(AdminAuthContext);

export function AdminAuthProvider({ children }) {
  const [admin, setAdmin] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getAdminToken();
    const info = localStorage.getItem(ADMIN_INFO_KEY);
    if (token && info) {
      setAdmin(JSON.parse(info));
    }
    setLoading(false);
  }, []);

  async function login(email, password) {
    const data = await apiFetch('/api/v1/admin/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
    const info = { adminId: data.adminId, email: data.email, role: data.role };
    localStorage.setItem(ADMIN_TOKEN_KEY, data.accessToken);
    localStorage.setItem(ADMIN_INFO_KEY, JSON.stringify(info));
    setAdmin(info);
    return info;
  }

  function logout() {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem(ADMIN_INFO_KEY);
    setAdmin(null);
  }

  return (
    <AdminAuthContext.Provider value={{ admin, login, logout, loading }}>
      {children}
    </AdminAuthContext.Provider>
  );
}

AdminAuthProvider.propTypes = {
  children: PropTypes.node.isRequired,
};
