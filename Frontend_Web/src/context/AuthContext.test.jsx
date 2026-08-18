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
  const { user, login, register, logout, updateProfile } = useAuth();

  // 🟢 Catch errors to prevent unhandled rejections in tests
  const handleLogin = async () => {
    try { await login('t@t.com', 'pw'); } catch (e) { /* silent */ }
  };

  return (
    <div>
      <div data-testid="user">{user ? user.username : 'no-user'}</div>
      <button onClick={handleLogin}>Login</button>
      <button onClick={() => register('u', 'e', 'p', 20, 'M')}>Register</button>
      <button onClick={() => updateProfile('new-nick', 21, 'F')}>Update</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
};

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('handles full auth lifecycle', async () => {
    api.post.mockResolvedValueOnce({
      data: { accessToken: 'tk1', userId: 1, username: 'user1', email: 't@t.com' }
    });

    render(<AuthProvider><TestComponent /></AuthProvider>);

    fireEvent.click(screen.getByText('Register'));
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('user1'));

    api.put.mockResolvedValueOnce({
      data: { username: 'new-nick', age: 21, gender: 'F' }
    });

    fireEvent.click(screen.getByText('Update'));
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('new-nick'));

    fireEvent.click(screen.getByText('Logout'));
    expect(screen.getByTestId('user').textContent).toBe('no-user');
  });

  it('handles login failure without crashing', async () => {
    api.post.mockRejectedValueOnce({ response: { data: { message: 'Wrong PW' } } });
    render(<AuthProvider><TestComponent /></AuthProvider>);

    fireEvent.click(screen.getByText('Login'));
    // State remains no-user
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('no-user'));
  });
});
