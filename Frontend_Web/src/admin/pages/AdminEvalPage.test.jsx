import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import AdminEvalPage from './AdminEvalPage';

const apiFetchMock = vi.fn();
vi.mock('../api', () => ({ apiFetch: (...args) => apiFetchMock(...args) }));

// Shapes match the deployed GET /api/v1/admin/agent-validations (PageResponse of audit rows);
// payloads are JSON strings, exactly as the backend stores them.
function importRow(id, email, rawContent, placeNames) {
  return {
    id,
    userEmail: email,
    operation: 'IMPORT',
    outcome: 'SUCCESS',
    requestPayload: JSON.stringify({ raw_content: rawContent, source_url: null }),
    responsePayload: JSON.stringify({ places: placeNames.map((name) => ({ name, type: 'attraction' })) }),
    createdAt: '2026-08-19T00:00:00Z',
  };
}

const PAGE = {
  content: [
    // gold heuristic finds Merlion Park, Marina Bay Sands, Sentosa Island (3);
    // model got 2 of them -> recall 2/3, precision 1, groundedness 1.
    importRow(10, 'traveler@example.com',
      'Day 1: Merlion Park and Marina Bay Sands. Day 2: Sentosa Island.',
      ['Merlion Park', 'Marina Bay Sands']),
    // REFINE and FAILED rows must be ignored.
    { id: 11, userEmail: 'x@example.com', operation: 'REFINE', outcome: 'SUCCESS', requestPayload: '{}', responsePayload: '{}', createdAt: '2026-08-19T00:00:00Z' },
    { id: 12, userEmail: 'y@example.com', operation: 'IMPORT', outcome: 'FAILED', requestPayload: '{}', responsePayload: '{}', createdAt: '2026-08-19T00:00:00Z' },
  ],
  totalPages: 1,
};

describe('AdminEvalPage (frontend-only scoring)', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it('reads the audit log, scores imports in-browser, and averages only IMPORT/SUCCESS rows', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    render(<AdminEvalPage />);

    await waitFor(() => expect(apiFetchMock)
      .toHaveBeenCalledWith('/api/v1/admin/agent-validations?page=0&size=50'));
    // Only the single IMPORT/SUCCESS row counts.
    expect(await screen.findByText('Average across 1 import')).toBeInTheDocument();
    expect(screen.getByText('traveler@example.com')).toBeInTheDocument();
    // recall = 2/3 -> 67%; precision & groundedness = 100%.
    expect(screen.getAllByText('67%').length).toBeGreaterThan(0);
    expect(screen.getAllByText('100%').length).toBeGreaterThan(0);
  });

  it('drills into an import to show matched and missed places', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    render(<AdminEvalPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Inspect' }));

    expect(await screen.findByText('Import #10 · traveler@example.com')).toBeInTheDocument();
    expect(screen.getByText(/Merlion Park and Marina Bay Sands/)).toBeInTheDocument();
    expect(screen.getByText('✓ Merlion Park')).toBeInTheDocument();   // matched
    expect(screen.getByText('✗ Sentosa Island')).toBeInTheDocument(); // missed by the model
  });

  it('shows an empty state when no successful imports exist', async () => {
    apiFetchMock.mockResolvedValue({ content: [], totalPages: 0 });
    render(<AdminEvalPage />);
    expect(await screen.findByText(/No successful imports have been recorded yet/)).toBeInTheDocument();
  });

  it('surfaces an error when the request fails', async () => {
    apiFetchMock.mockRejectedValue(new Error('boom'));
    render(<AdminEvalPage />);
    await waitFor(() => expect(screen.getByText('boom')).toBeInTheDocument());
  });
});
