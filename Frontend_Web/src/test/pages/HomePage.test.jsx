import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 Setup mocks BEFORE component import
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: null }),
}));
vi.mock('../../context/TripContext', () => ({
  useTrip: () => ({ getActiveTrip: () => null }),
}));

import HomePage from '../../pages/HomePage';

describe('HomePage', () => {
  it('renders hero section with title and search box', () => {
    render(
      <BrowserRouter>
        <HomePage />
      </BrowserRouter>
    );

    expect(screen.getByText(/Hello, where are you going?/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/Search for destinations/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /Smart Planning/i })).toBeDefined();
  });

  it('does not show Active Itinerary section when user is not logged in', () => {
    render(
      <BrowserRouter>
        <HomePage />
      </BrowserRouter>
    );

    expect(screen.queryByText(/Active Itinerary/i)).toBeNull();
  });
});
