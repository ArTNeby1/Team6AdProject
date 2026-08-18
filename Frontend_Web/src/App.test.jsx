import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import App from './App';

// Mock high-level components to test routing without context overhead
vi.mock('./TravelerApp', () => ({
  default: () => <div data-testid="traveler-app">Traveler App</div>,
}));

vi.mock('./admin/pages/AdminLoginPage', () => ({
  default: () => <div data-testid="admin-login">Admin Login</div>,
}));

// Mock providers as well
vi.mock('./admin/AdminAuthContext', () => ({
  AdminAuthProvider: ({ children }) => <div>{children}</div>,
}));

describe('App Routing', () => {
  it('renders traveler app by default', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );
    expect(screen.getByTestId('traveler-app')).toBeInTheDocument();
  });

  it('renders admin login page on /admin/login', () => {
    render(
      <MemoryRouter initialEntries={['/admin/login']}>
        <App />
      </MemoryRouter>
    );
    expect(screen.getByTestId('admin-login')).toBeInTheDocument();
  });
});
