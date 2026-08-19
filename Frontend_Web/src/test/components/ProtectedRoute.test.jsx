import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import ProtectedRoute from '../../components/ProtectedRoute';

// 🟢 Using hoisted mock to prevent reference issues
const { mockAuthStatus } = vi.hoisted(() => ({
  mockAuthStatus: { user: null, loading: false }
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => mockAuthStatus
}));

describe('ProtectedRoute', () => {
  it('renders loading state correctly', () => {
    mockAuthStatus.user = null;
    mockAuthStatus.loading = true;

    render(
      <MemoryRouter>
        <ProtectedRoute>
          <div data-testid="child">Secret</div>
        </ProtectedRoute>
      </MemoryRouter>
    );

    expect(screen.getByText(/Loading.../i)).toBeInTheDocument();
  });

  it('renders children when authenticated', () => {
    mockAuthStatus.user = { id: 1 };
    mockAuthStatus.loading = false;

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/protected" element={
            <ProtectedRoute>
              <div data-testid="child">Secret Content</div>
            </ProtectedRoute>
          } />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByTestId('child')).toBeInTheDocument();
  });

  it('redirects to login when not authenticated', () => {
    mockAuthStatus.user = null;
    mockAuthStatus.loading = false;

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route path="/protected" element={
            <ProtectedRoute>
              <div data-testid="child">Secret</div>
            </ProtectedRoute>
          } />
          <Route path="/login" element={<div data-testid="login-page">Login Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });
});
