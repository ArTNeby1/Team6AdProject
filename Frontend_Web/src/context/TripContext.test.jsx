import React from 'react';
import { render, waitFor, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// 🟢 所有要在 vi.mock 中使用的变量都必须放在 vi.hoisted 块中
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
  const { trips } = useTrip();
  return <div data-testid="trips-count">{trips.length}</div>;
};

describe('TripContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches trips and updates state', async () => {
    const mockTrips = [
      {
        id: 1,
        tripName: 'Trip A',
        startDate: '2026-01-01',
        status: 'ACTIVE',
        durationDays: 1,
        schedules: []
      }
    ];

    // 🟢 在渲染前设置 mock 返回值
    mockApi.get.mockResolvedValue({ data: mockTrips });

    render(
      <TripProvider>
        <TestComponent />
      </TripProvider>
    );

    // 增加超时时间并确保 DOM 已更新
    await waitFor(() => {
      const element = screen.getByTestId('trips-count');
      expect(element.textContent).toBe('1');
    }, { timeout: 4000 });

    expect(mockApi.get).toHaveBeenCalledWith('/trips');
  });
});
