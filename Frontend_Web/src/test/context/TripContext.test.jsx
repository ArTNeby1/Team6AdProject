import React from 'react';
import { render, waitFor, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockUser, mockApi } = vi.hoisted(() => ({
  mockUser: { id: 1 },
  mockApi: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }
}));

vi.mock('../../services/api', () => ({
  __esModule: true,
  default: mockApi,
  ...mockApi
}));

vi.mock('../../context/AuthContext', () => ({
  __esModule: true,
  useAuth: () => ({ user: mockUser, loading: false }),
}));

import { TripProvider, useTrip } from '../../context/TripContext';

const TestComponent = () => {
  const { trips, createNewTrip, deleteTrip, updateTripTitle, updateTripDate, deleteDay, fetchAttractionData } = useTrip();
  return (
    <div>
      <div data-testid="count">{trips.length}</div>
      <button onClick={() => createNewTrip(['Place'])}>Create</button>
      <button onClick={() => deleteTrip('1')}>Delete</button>
      <button onClick={() => updateTripTitle('1', 'New Title')}>UpdateTitle</button>
      <button onClick={() => updateTripDate('1', '2026-10-10')}>UpdateDate</button>
      <button onClick={() => deleteDay('1', 1)}>DeleteDay</button>
      <button onClick={() => fetchAttractionData('Place')}>FetchAttraction</button>
    </div>
  );
};

describe('TripContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('handles trip management operations', async () => {
    // 1. Initial State
    mockApi.get.mockResolvedValue({ data: [{ id: 1, tripName: 'Trip 1' }] });
    render(<TripProvider><TestComponent /></TripProvider>);
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'));

    // 2. Create Trip
    mockApi.post.mockResolvedValue({ data: { id: 2 } });
    mockApi.get.mockResolvedValue({ data: [{ id: 1, tripName: 'Trip 1' }, { id: 2, tripName: 'Trip 2' }] });
    fireEvent.click(screen.getByText('Create'));
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('2'));

    // 3. Update Title
    mockApi.put.mockResolvedValue({ data: {} });
    fireEvent.click(screen.getByText('UpdateTitle'));

    // 4. Update Date
    fireEvent.click(screen.getByText('UpdateDate'));

    // 5. Delete Day
    mockApi.delete.mockResolvedValue({ data: {} });
    fireEvent.click(screen.getByText('DeleteDay'));

    // 6. Fetch Attraction
    mockApi.get.mockResolvedValue({ data: [{ name: 'Place' }] });
    fireEvent.click(screen.getByText('FetchAttraction'));

    // 7. Delete Trip
    mockApi.delete.mockResolvedValue({ data: {} });
    mockApi.get.mockResolvedValue({ data: [{ id: 2, tripName: 'Trip 2' }] });
    fireEvent.click(screen.getByText('Delete'));
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'));
  });
});
