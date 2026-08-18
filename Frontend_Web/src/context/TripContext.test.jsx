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

vi.mock('../services/api', () => ({
  __esModule: true,
  default: mockApi,
  ...mockApi
}));

vi.mock('./AuthContext', () => ({
  __esModule: true,
  useAuth: () => ({ user: mockUser, loading: false }),
}));

import { TripProvider, useTrip } from './TripContext';

const TestComponent = () => {
  const { trips, createNewTrip, deleteTrip } = useTrip();
  return (
    <div>
      <div data-testid="count">{trips.length}</div>
      <button onClick={() => createNewTrip(['Place'])}>Create</button>
      <button onClick={() => deleteTrip('1')}>Delete</button>
    </div>
  );
};

describe('TripContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('handles trip management operations', async () => {
    // 1. Initial State: Return 1 trip
    mockApi.get.mockResolvedValue({ data: [{ id: 1, tripName: 'Trip 1' }] });

    render(<TripProvider><TestComponent /></TripProvider>);
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'));

    // 2. Create Trip Step
    // When create is clicked, it will call GET /trips multiple times (due to redundant calls in Context)
    // 🟢 We set the mock to return 2 trips for ALL subsequent GET calls in this stage
    mockApi.post.mockResolvedValue({ data: { id: 2 } });
    mockApi.get.mockResolvedValue({ data: [
        { id: 1, tripName: 'Trip 1' },
        { id: 2, tripName: 'Trip 2' }
    ] });

    fireEvent.click(screen.getByText('Create'));
    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('2'));

    // 3. Delete Trip '1'
    mockApi.delete.mockResolvedValue({ data: {} });
    // 🟢 After delete, all subsequent GET calls should return only the remaining trip
    mockApi.get.mockResolvedValue({ data: [
        { id: 2, tripName: 'Trip 2' }
    ] });

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
        expect(screen.getByTestId('count').textContent).toBe('1');
    });
  });
});
