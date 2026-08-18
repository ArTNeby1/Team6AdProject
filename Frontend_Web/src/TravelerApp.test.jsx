import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import TravelerApp from './TravelerApp';

// 🟢 Mock out the sub-pages to save massive memory during coverage
vi.mock('./pages/HomePage', () => ({ default: () => <div data-testid="mock-home">Home</div> }));
vi.mock('./pages/LoginPage', () => ({ default: () => <div>Login</div> }));

vi.mock('./context/AuthContext', () => ({
  useAuth: () => ({ user: null, loading: false }),
}));
vi.mock('./context/TripContext', () => ({
  useTrip: () => ({ trips: [], loadingTrips: false, fetchTrips: vi.fn() }),
}));

describe('TravelerApp', () => {
  it('renders correctly with layout shell', () => {
    render(
      <BrowserRouter>
        <TravelerApp />
      </BrowserRouter>
    );

    // Verify layout elements are present
    const brands = screen.getAllByText(/LoomyTrip/i);
    expect(brands.length).toBeGreaterThan(0);
  });
});
