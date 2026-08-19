import React from 'react';
import { Routes, Route } from 'react-router-dom';
import TravelerApp from './TravelerApp';
import { AdminAuthProvider } from './admin/AdminAuthContext';
import AdminProtectedRoute from './admin/AdminProtectedRoute';
import AdminLayout from './admin/AdminLayout';
import AdminLoginPage from './admin/pages/AdminLoginPage';
import AdminDashboardPage from './admin/pages/AdminDashboardPage';
import AdminUsersPage from './admin/pages/AdminUsersPage';
import AdminEvalPage from './admin/pages/AdminEvalPage';
import AdminEvalDetailPage from './admin/pages/AdminEvalDetailPage';
import './admin/admin.css';

// Top-level split: the admin console (its own chrome + auth) lives under
// /admin/*, everything else is the traveler app.
function App() {
  return (
    <Routes>
      <Route
        path="/admin/*"
        element={
          <AdminAuthProvider>
            <Routes>
              <Route path="login" element={<AdminLoginPage />} />
              <Route
                element={
                  <AdminProtectedRoute>
                    <AdminLayout />
                  </AdminProtectedRoute>
                }
              >
                <Route index element={<AdminDashboardPage />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="eval" element={<AdminEvalPage />} />
                <Route path="eval/:id" element={<AdminEvalDetailPage />} />
              </Route>
            </Routes>
          </AdminAuthProvider>
        }
      />
      <Route path="/*" element={<TravelerApp />} />
    </Routes>
  );
}

export default App;
