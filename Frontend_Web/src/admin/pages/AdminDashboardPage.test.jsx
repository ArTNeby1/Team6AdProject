import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

// vi.hoisted so the mock fn exists before the (hoisted) vi.mock factories run.
const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock('../AdminAuthContext', () => ({
  useAdminAuth: () => ({ admin: { email: 'admin@loomytrip.local', role: 'super_admin' } }),
}));
vi.mock('../api', () => ({ apiFetch: apiFetchMock }));

import AdminDashboardPage from './AdminDashboardPage';

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it('shows the live user count fetched from the admin API', async () => {
    apiFetchMock.mockResolvedValue({ totalElements: 7 });
    render(<AdminDashboardPage />);

    // The role card renders immediately from context.
    expect(screen.getByText('super_admin')).toBeInTheDocument();
    // The total-users value appears once the fetch resolves.
    await waitFor(() => expect(screen.getByText('7')).toBeInTheDocument());
    expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/admin/users?page=0&size=1');
  });

  it('shows an error alert when the metrics request fails', async () => {
    apiFetchMock.mockRejectedValue(new Error('boom'));
    render(<AdminDashboardPage />);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('boom'));
  });
});
