import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 Use hoisted for stable mock variables
const { mockAuth, mockTrip } = vi.hoisted(() => ({
  mockAuth: { user: { id: 1 } },
  mockTrip: {
    trips: [
      { id: '1', title: 'Trip to Thailand', status: 'ACTIVE', dayCount: 3, locations: [] }
    ],
    addLocationsToTripDay: vi.fn()
  }
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: () => mockAuth
}));

vi.mock('../context/TripContext', () => ({
  useTrip: () => mockTrip
}));

import AttractionPage from './AttractionPage';

describe('AttractionPage', () => {
  it('renders attraction details correctly', () => {
    render(
      <BrowserRouter>
        <AttractionPage />
      </BrowserRouter>
    );

    // 🟢 Use getAllByText because the name appears in both title and subtitle
    expect(screen.getAllByText(/Wat Chedi Luang/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Attraction Introduction/i)).toBeInTheDocument();
    expect(screen.getByText(/Tourist Reviews/i)).toBeInTheDocument();
  });

  it('opens picker when "Add to Itinerary" is clicked', () => {
    render(
      <BrowserRouter>
        <AttractionPage />
      </BrowserRouter>
    );

    const addBtn = screen.getByText(/Add to Itinerary/i);
    fireEvent.click(addBtn);

    expect(screen.getByText(/Select Target Itinerary/i)).toBeInTheDocument();
    expect(screen.getByText(/Trip to Thailand/i)).toBeInTheDocument();
  });
});
