import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 Setup mock for the hook explicitly
vi.mock('../../context/TripContext', () => ({
  useTrip: vi.fn(() => ({
    itinerary: [],
    trips: [],
    loadingTrips: false,
  }))
}));

import RoutePage from '../../pages/RoutePage';

describe('RoutePage', () => {
  it('renders route page correctly', () => {
    render(
      <BrowserRouter>
        <RoutePage />
      </BrowserRouter>
    );

    // Matches kicker text in RoutePage.jsx
    expect(screen.getByText(/My Trip Itinerary/i)).toBeInTheDocument();

    // 🟢 Use getAllByText for non-unique strings like "Chiang Mai"
    expect(screen.getAllByText(/Chiang Mai/i).length).toBeGreaterThan(0);
  });
});
