import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 Use a stable mock object
const mockAuth = {
  user: { id: 1, username: 'testuser', email: 'test@example.com', age: 25, gender: 'Male' },
  updateProfile: vi.fn(),
  logout: vi.fn(),
  loading: false
};

const mockTrip = {
  trips: [],
  loadingTrips: false
};

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => mockAuth
}));

vi.mock('../../context/TripContext', () => ({
  useTrip: () => mockTrip
}));

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: { id: 1, username: 'testuser', email: 'test@example.com' } }),
    put: vi.fn().mockResolvedValue({ data: {} })
  }
}));

import ProfilePage from '../../pages/ProfilePage';

describe('ProfilePage', () => {
  it('renders profile correctly', async () => {
    render(
      <BrowserRouter>
        <ProfilePage />
      </BrowserRouter>
    );

    // Matches the ID display in ProfilePage.jsx
    expect(screen.getByText(/ID: test@example.com/i)).toBeInTheDocument();

    // Matches the username display
    await waitFor(() => {
      expect(screen.getByText(/testuser/i)).toBeInTheDocument();
    });
  });
});
