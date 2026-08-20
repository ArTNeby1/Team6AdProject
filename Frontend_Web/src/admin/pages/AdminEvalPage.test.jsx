import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
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
    // Scoreable: gold heuristic finds Merlion Park, Marina Bay Sands, Sentosa Island (3);
    // model got 2 -> recall 2/3, precision 1, groundedness 1.
    importRow(10, 'traveler@example.com',
      'Day 1: Merlion Park and Marina Bay Sands. Day 2: Sentosa Island.',
      ['Merlion Park', 'Marina Bay Sands']),
    // REFINE and FAILED rows must be ignored.
    { id: 11, userEmail: 'x@example.com', operation: 'REFINE', outcome: 'SUCCESS', requestPayload: '{}', responsePayload: '{}', createdAt: '2026-08-19T00:00:00Z' },
    { id: 12, userEmail: 'y@example.com', operation: 'IMPORT', outcome: 'FAILED', requestPayload: '{}', responsePayload: '{}', createdAt: '2026-08-19T00:00:00Z' },
    // Unscoreable: lowercase place names -> heuristic finds no gold -> P/R/F1 = N/A,
    // but both places are in the text so groundedness = 100%.
    importRow(13, 'casual@example.com',
      'We would also like to visit sentosa and arab street.',
      ['Sentosa', 'Arab Street']),
  ],
  totalPages: 1,
};

function renderPage() {
  return render(<MemoryRouter><AdminEvalPage /></MemoryRouter>);
}

describe('AdminEvalPage (frontend-only scoring)', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it('reads the audit log, scores imports in-browser, and averages only IMPORT/SUCCESS rows', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    renderPage();

    await waitFor(() => expect(apiFetchMock)
      .toHaveBeenCalledWith('/api/v1/admin/agent-validations?page=0&size=50'));
    // Two IMPORT/SUCCESS rows (10 + 13); REFINE/FAILED ignored.
    expect(await screen.findByText('Average across 2 imports')).toBeInTheDocument();
    expect(screen.getByText('traveler@example.com')).toBeInTheDocument();
    expect(screen.getByText('casual@example.com')).toBeInTheDocument();
    // recall = 2/3 -> 67%; precision & groundedness = 100%.
    expect(screen.getAllByText('67%').length).toBeGreaterThan(0);
    expect(screen.getAllByText('100%').length).toBeGreaterThan(0);
  });

  it('shows N/A (not 0%) for the unscoreable import and averages P/R/F1 over scoreable only', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    renderPage();

    // The lowercase import row surfaces N/A for its unscoreable metrics.
    await waitFor(() => expect(screen.getAllByText('N/A').length).toBeGreaterThan(0));
    // Honest note about the partial coverage.
    expect(screen.getByText(/averaged over 1 of 2 imports/)).toBeInTheDocument();
    // Zero must never appear as a metric value (the whole point of the patch).
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
  });

  it('links each import to its own detail route', async () => {
    apiFetchMock.mockResolvedValue(PAGE);
    renderPage();

    const inspects = await screen.findAllByRole('link', { name: 'Inspect' });
    expect(inspects[0]).toHaveAttribute('href', '/admin/eval/10');
  });

  it('shows an empty state when no successful imports exist', async () => {
    apiFetchMock.mockResolvedValue({ content: [], totalPages: 0 });
    renderPage();
    expect(await screen.findByText(/No successful imports have been recorded yet/)).toBeInTheDocument();
  });

  it('surfaces an error when the request fails', async () => {
    apiFetchMock.mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getByText('boom')).toBeInTheDocument());
  });
});

describe('AdminEvalPage pagination', () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  // 7 scoreable imports so paging is exercised at the default page size of 5.
  const MANY = {
    content: Array.from({ length: 7 }, (_, i) =>
      importRow(20 + i, `u${i + 1}@example.com`, 'Visited Merlion Park today.', ['Merlion Park'])),
    totalPages: 1,
  };
  const rowCount = () => screen.getAllByRole('link', { name: 'Inspect' }).length;

  it('shows only the first page (default 5) and the total count', async () => {
    apiFetchMock.mockResolvedValue(MANY);
    renderPage();

    await waitFor(() => expect(screen.getByLabelText('Rows per page')).toBeInTheDocument());
    expect(rowCount()).toBe(5);                      // 5 of 7 on page 1
    expect(screen.getByText('of 7')).toBeInTheDocument();
    expect(screen.getByText('Page 1 / 2')).toBeInTheDocument();
    expect(screen.getByText('1–5 of 7')).toBeInTheDocument();
  });

  it('advances to the next page with the remaining rows', async () => {
    apiFetchMock.mockResolvedValue(MANY);
    renderPage();
    await waitFor(() => expect(screen.getByText('Page 1 / 2')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(rowCount()).toBe(2);                      // remaining 2 on page 2
    expect(screen.getByText('6–7 of 7')).toBeInTheDocument();
    expect(screen.getByText('Page 2 / 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('changing the page size shows more rows and resets to page 1', async () => {
    apiFetchMock.mockResolvedValue(MANY);
    renderPage();
    await waitFor(() => expect(screen.getByText('Page 1 / 2')).toBeInTheDocument());

    // Go to page 2 first, then grow the page size — should snap back to page 1 with all rows.
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    fireEvent.change(screen.getByLabelText('Rows per page'), { target: { value: '10' } });

    expect(rowCount()).toBe(7);
    expect(screen.getByText('1–7 of 7')).toBeInTheDocument();
    expect(screen.getByText('Page 1 / 1')).toBeInTheDocument();
  });
});
