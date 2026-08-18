import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import Header from './Header';

// 🟢 Use a stable mock for context
const { mockAuth } = vi.hoisted(() => ({
  mockAuth: { user: null, logout: vi.fn() }
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => mockAuth,
}));

describe('Header', () => {
  it('renders navigation links', () => {
    mockAuth.user = null;
    render(<BrowserRouter><Header /></BrowserRouter>);
    expect(screen.getByText(/Home/i)).toBeInTheDocument();
    expect(screen.getByText(/Login/i)).toBeInTheDocument();
  });

  it('shows user info when logged in', () => {
    mockAuth.user = { username: 'JohnDoe', email: 'john@test.com' };
    render(<BrowserRouter><Header /></BrowserRouter>);

    // Header displays first letter of name
    expect(screen.getByText('J')).toBeInTheDocument();
    expect(screen.getByText('JohnDoe')).toBeInTheDocument();
    expect(screen.getByText(/Logout/i)).toBeInTheDocument();
  });
});
