import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminEvalPage from './AdminEvalPage';

const apiFetchMock = vi.fn();
vi.mock('../api', () => ({ apiFetch: (...args) => apiFetchMock(...args) }));

describe('AdminEvalPage', () => {
  beforeEach(() => {
    apiFetchMock.mockResolvedValue({
      content: [{
        id: 1, operation: 'IMPORT', userEmail: 'traveler@example.com', outcome: 'SUCCESS',
        requestPayload: '{"raw_content":"Singapore trip"}',
        responsePayload: '{"places":[]}', createdAt: '2026-08-16T00:00:00Z',
      }],
      totalPages: 1,
    });
  });

  it('loads live validation records and displays a request-response comparison', async () => {
    render(<AdminEvalPage />);
    await waitFor(() => expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/admin/agent-validations?page=0&size=20'));
    expect(await screen.findByText('traveler@example.com')).toBeInTheDocument();
    fireEvent.click(await screen.findByRole('button', { name: 'Compare' }));
    expect(await screen.findByText(/Singapore trip/)).toBeInTheDocument();
    expect(await screen.findByText(/"places"/)).toBeInTheDocument();
  });
});
