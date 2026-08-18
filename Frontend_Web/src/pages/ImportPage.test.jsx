import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import ImportPage from './ImportPage';

// 🟢 Setup mocks before component import
vi.mock('../services/api', () => ({
  __esModule: true,
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  }
}));

vi.mock('../context/AuthContext', () => ({
  __esModule: true,
  useAuth: () => ({ user: { id: 1 } }),
}));

vi.mock('../context/TripContext', () => ({
  __esModule: true,
  useTrip: () => ({ fetchTrips: vi.fn(), addLocationsToTripDay: vi.fn() }),
}));

import api from '../services/api';

describe('ImportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders input area initially', () => {
    render(
      <BrowserRouter>
        <ImportPage />
      </BrowserRouter>
    );

    expect(screen.getByPlaceholderText(/Paste or enter your travel notes here/i)).toBeInTheDocument();
  });

  it('handles parsing and displays results', async () => {
    const mockSession = { data: { id: 123, status: 'DRAFT_READY', durationDays: 1 } };
    const mockDetail = {
      data: {
        id: 123,
        durationDays: 1,
        draftPlaces: [
          { id: 1, name: 'Gardens by the Bay', validationStatus: 'VALID', activities: [] }
        ]
      }
    };

    api.post.mockResolvedValueOnce(mockSession); // create session
    api.post.mockResolvedValueOnce({}); // validate-places
    api.get.mockResolvedValueOnce(mockDetail); // load detail

    render(
      <BrowserRouter>
        <ImportPage />
      </BrowserRouter>
    );

    const textarea = screen.getByPlaceholderText(/Paste or enter your travel notes here/i);
    fireEvent.change(textarea, { target: { value: 'Visit Gardens by the Bay' } });
    fireEvent.click(screen.getByRole('button', { name: /Start Parsing/i }));

    await waitFor(() => {
      expect(screen.getByText(/LoomyTrip AI Agent/i)).toBeInTheDocument();
      expect(screen.getByDisplayValue(/Gardens by the Bay/i)).toBeInTheDocument();
    });
  });
});
