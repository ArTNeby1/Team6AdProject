import React from 'react';
import { render, waitFor, fireEvent, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import api from '../services/api';

vi.mock('../services/api', () => ({
  default: {
    post: vi.fn(),
    put: vi.fn(),
    get: vi.fn()
  }
}));

const TestComponent = () => {
  const { user, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="user">{user ? user.username : 'no-user'}</div>
      <button onClick={() => login('test@example.com', 'password')}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
};

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('handles login and logout flow', async () => {
    api.post.mockResolvedValueOnce({
      data: { accessToken: 'tk', userId: 1, username: 'tester', email: 't@t.com' }
    });

    render(<AuthProvider><TestComponent /></AuthProvider>);

    // 1. Initial State
    expect(screen.getByTestId('user').textContent).toBe('no-user');

    // 2. Login
    fireEvent.click(screen.getByText('Login'));
    await waitFor(() => {
      expect(screen.getByTestId('user').textContent).toBe('tester');
    });

    // 3. Logout
    fireEvent.click(screen.getByText('Logout'));
    expect(screen.getByTestId('user').textContent).toBe('no-user');
  });
});
