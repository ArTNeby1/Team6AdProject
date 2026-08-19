import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminEvalDetailPage from './AdminEvalDetailPage';

const apiFetchMock = vi.fn();
vi.mock('../api', () => ({ apiFetch: (...args) => apiFetchMock(...args) }));

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
    importRow(10, 'traveler@example.com',
      'Day 1: Merlion Park and Marina Bay Sands. Day 2: Sentosa Island.',
      ['Merlion Park', 'Marina Bay Sands']),
  ],
  totalPages: 1,
};

// Render the detail page at /admin/eval/:id so useParams resolves.
function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/eval/:id" element={<AdminEvalDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminEvalDetailPage', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it('finds the import by id and shows its metrics, source text, and place chips', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    renderAt('/admin/eval/10');

    expect(await screen.findByText('traveler@example.com', { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/Merlion Park and Marina Bay Sands/)).toBeInTheDocument();
    expect(screen.getByText('✓ Merlion Park')).toBeInTheDocument();   // matched
    expect(screen.getByText('✗ Sentosa Island')).toBeInTheDocument(); // missed by the model
    // recall = 2/3 -> 67% present in the scorecard.
    expect(screen.getAllByText('67%').length).toBeGreaterThan(0);
  });

  it('shows a not-found message when the id is absent from recent imports', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    renderAt('/admin/eval/999');
    expect(await screen.findByText(/was not found among the 50 most recent imports/)).toBeInTheDocument();
  });

  it('surfaces an error when the request fails', async () => {
    apiFetchMock.mockRejectedValue(new Error('boom'));
    renderAt('/admin/eval/10');
    await waitFor(() => expect(screen.getByText('boom')).toBeInTheDocument());
  });
});
