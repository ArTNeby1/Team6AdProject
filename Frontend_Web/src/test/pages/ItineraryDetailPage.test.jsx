import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ItineraryDetailPage from '../../pages/ItineraryDetailPage';
import { useTrip } from '../../context/TripContext';

vi.mock('../../context/TripContext', () => ({
  __esModule: true,
  useTrip: vi.fn(),
}));

vi.mock('../../services/api', () => ({
  __esModule: true,
  mapApi: {
    getRoute: vi.fn().mockResolvedValue({ data: { transports: [] } }),
  },
}));

describe('ItineraryDetailPage', () => {
  const mockTrip = {
    id: '1',
    title: 'Singapore Trip',
    date: '2026-12-05',
    dayCount: 3,
    locations: [
      { id: '101', name: 'Gardens by the Bay', day: 1, time: '09:00' }
    ],
    status: 'ACTIVE'
  };

  beforeEach(() => {
    useTrip.mockReturnValue({
      getTripById: () => mockTrip,
      getActiveTrip: () => mockTrip,
      setActiveTripId: vi.fn(),
      loadingTrips: false,
    });
  });

  it('renders trip title and days', async () => {
    useTrip.mockReturnValue({
      getTripById: () => mockTrip,
      getActiveTrip: () => mockTrip,
      setActiveTripId: vi.fn(),
      loadingTrips: false,
      addDayToTrip: vi.fn(),
      addLocationsToTripDay: vi.fn(),
      updateTripTitle: vi.fn(),
      updateTripDate: vi.fn(),
    });

    // 🟢 Wrap render in act for async state updates
    await act(async () => {
      render(
        <BrowserRouter>
          <ItineraryDetailPage />
        </BrowserRouter>
      );
    });

    await waitFor(() => {
      expect(screen.getByText(/Singapore Trip/i)).toBeInTheDocument();
      const dayElements = screen.getAllByText(/Day 1:/i);
      expect(dayElements.length).toBeGreaterThan(0);
      expect(screen.getByText(/Gardens by the Bay/i)).toBeInTheDocument();
    });

    // 🟢 Trigger Title Edit to hit more lines
    const editBtn = screen.getByTitle(/Edit trip title/i);
    fireEvent.click(editBtn);
    expect(screen.getByDisplayValue(/Singapore Trip/i)).toBeInTheDocument();
  });
});
