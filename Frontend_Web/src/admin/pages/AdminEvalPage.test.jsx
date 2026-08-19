import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminEvalPage from './AdminEvalPage';

const apiFetchMock = vi.fn();
vi.mock('../api', () => ({ apiFetch: (...args) => apiFetchMock(...args) }));

const SUMMARY = {
  totalCount: 2,
  scoredCount: 2,
  averages: { precision: 0.75, recall: 0.5, f1: 0.6, groundedness: 1.0 },
  records: [
    {
      id: 10,
      userEmail: 'traveler@example.com',
      operation: 'IMPORT',
      createdAt: '2026-08-16T00:00:00Z',
      available: true,
      precision: 1.0,
      recall: 0.5,
      f1: 0.67,
      groundedness: 1.0,
      sourceText: 'Day 1: Gardens by the Bay. Day 2: Merlion Park and Sentosa.',
      predictedPlaces: ['Gardens by the Bay', 'Merlion Park'],
      goldPlaces: ['Gardens by the Bay', 'Merlion Park', 'Sentosa'],
      matched: ['Gardens by the Bay', 'Merlion Park'],
      missed: ['Sentosa'],
      spurious: [],
    },
    {
      id: 11,
      userEmail: 'other@example.com',
      operation: 'IMPORT',
      createdAt: '2026-08-15T00:00:00Z',
      available: true,
      precision: 0.5,
      recall: 0.5,
      f1: 0.5,
      groundedness: 1.0,
      sourceText: 'Marina Bay Sands trip',
      predictedPlaces: ['Marina Bay Sands'],
      goldPlaces: ['Marina Bay Sands', 'Gardens by the Bay'],
      matched: ['Marina Bay Sands'],
      missed: ['Gardens by the Bay'],
      spurious: [],
    },
  ],
};

describe('AdminEvalPage', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it('shows the averaged scorecard across scored imports', async () => {
    apiFetchMock.mockResolvedValue(SUMMARY);
    render(<AdminEvalPage />);

    await waitFor(() => expect(apiFetchMock)
      .toHaveBeenCalledWith('/api/v1/admin/agent-validations/evaluations?limit=50'));
    expect(await screen.findByText('Average across 2 imports')).toBeInTheDocument();
    expect(screen.getByText('75%')).toBeInTheDocument(); // avg precision
    expect(screen.getByText('traveler@example.com')).toBeInTheDocument();
  });

  it('drills into a single import to show matched and missed places', async () => {
    apiFetchMock.mockResolvedValue(SUMMARY);
    render(<AdminEvalPage />);

    const inspectButtons = await screen.findAllByRole('button', { name: 'Inspect' });
    fireEvent.click(inspectButtons[0]);

    expect(await screen.findByText('Import #10 · traveler@example.com')).toBeInTheDocument();
    expect(screen.getByText(/Day 1: Gardens by the Bay/)).toBeInTheDocument();
    expect(screen.getByText('✗ Sentosa')).toBeInTheDocument(); // the missed place
    expect(screen.getByText('✓ Merlion Park')).toBeInTheDocument(); // a matched place
  });

  it('renders an empty-state message when there are no imports', async () => {
    apiFetchMock.mockResolvedValue({ totalCount: 0, scoredCount: 0, averages: {}, records: [] });
    render(<AdminEvalPage />);
    expect(await screen.findByText(/No imports have been recorded yet/)).toBeInTheDocument();
  });

  it('surfaces an error when the request fails', async () => {
    apiFetchMock.mockRejectedValue(new Error('boom'));
    render(<AdminEvalPage />);
    await waitFor(() => expect(screen.getByText('boom')).toBeInTheDocument());
  });
});
