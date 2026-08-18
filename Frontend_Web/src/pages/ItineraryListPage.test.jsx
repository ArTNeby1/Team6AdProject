import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// 🟢 Stable hoisted mocks
const { mockDeleteTrip, mockTripContext, mockAuthContext } = vi.hoisted(() => ({
  mockDeleteTrip: vi.fn(),
  mockTripContext: {
    trips: [
      { id: '1', title: 'Trip to Singapore', date: '2026-12-05', dayCount: 3, locations: [], shortName: 'SG', status: 'ACTIVE' },
      { id: '2', title: 'Trip to Bangkok', date: '2027-01-10', dayCount: 5, locations: [], shortName: 'BK', status: 'ACTIVE' }
    ],
    loadingTrips: false,
    setActiveTripId: vi.fn(),
    createNewTrip: vi.fn(),
    refreshTrips: vi.fn(),
  },
  mockAuthContext: {
    user: { id: 1, travelStyle: 'Cultural', preferTransport: 'Public' }
  }
}));

vi.mock('../context/TripContext', () => ({
  useTrip: () => ({
    ...mockTripContext,
    deleteTrip: mockDeleteTrip
  })
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => mockAuthContext
}));

import ItineraryListPage from './ItineraryListPage';

describe('ItineraryListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders list of trips', () => {
    render(
      <BrowserRouter>
        <ItineraryListPage />
      </BrowserRouter>
    );

    expect(screen.getByText(/My Itineraries/i)).toBeInTheDocument();
    expect(screen.getByText(/Trip to Singapore/i)).toBeInTheDocument();
  });

  it('handles trip deletion', async () => {
    window.confirm = vi.fn(() => true);

    render(
      <BrowserRouter>
        <ItineraryListPage />
      </BrowserRouter>
    );

    // Title must match exactly what is in ItineraryListPage.jsx: "Delete Itinerary"
    const deleteBtns = screen.getAllByTitle(/Delete Itinerary/i);
    fireEvent.click(deleteBtns[0]);

    expect(window.confirm).toHaveBeenCalled();
    expect(mockDeleteTrip).toHaveBeenCalledWith('1');
  });
});
