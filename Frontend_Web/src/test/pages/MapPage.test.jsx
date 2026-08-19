import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 1. 穩定的 Leaflet Mock (Hoisted)
const { mockTripData, mockMapInstance, mockL } = vi.hoisted(() => {
  const trip = {
    id: '1',
    title: 'Test Trip',
    dayCount: 1,
    status: 'ACTIVE',
    locations: [{ id: '101', name: 'Place 1', day: 1, lat: 1.35, lng: 103.8 }]
  };

  const map = {
    flyTo: vi.fn(),
    getZoom: vi.fn(() => 12),
    setView: vi.fn(),
    removeLayer: vi.fn(),
    addLayer: vi.fn(),
  };

  const LMock = {
    Icon: vi.fn(function() { this.options = {}; }),
    DivIcon: vi.fn(function() { this.options = {}; }),
    divIcon: vi.fn(() => ({ options: {} })),
    latLng: vi.fn((lat, lng) => ({ lat, lng })),
    heatLayer: vi.fn(() => ({ addTo: vi.fn(), remove: vi.fn() })),
  };
  LMock.Icon.Default = { prototype: { _getIconUrl: vi.fn() }, mergeOptions: vi.fn() };

  return {
    mockTripData: {
      trips: [trip],
      activeTripId: '1',
      getActiveTrip: () => trip,
      setActiveTripId: vi.fn(),
    },
    mockMapInstance: map,
    mockL: LMock
  };
});

globalThis.L = mockL;

vi.mock('leaflet', () => ({
  default: mockL,
  ...mockL
}));

// 🟢 2. 穩定 Mock react-leaflet
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => <div data-testid="tile-layer" />,
  Marker: ({ children }) => <div data-testid="marker">{children}</div>,
  Popup: ({ children }) => <div data-testid="popup">{children}</div>,
  Polyline: () => <div data-testid="polyline" />,
  useMap: () => mockMapInstance,
  useMapEvents: vi.fn(),
}));

vi.mock('leaflet.heat', () => ({ default: vi.fn() }));

vi.mock('../../context/TripContext', () => ({
  useTrip: () => mockTripData
}));

vi.mock('../../services/api', () => ({
  mapApi: {
    getConfig: vi.fn().mockResolvedValue({ data: {} }),
    getRoute: vi.fn().mockResolvedValue({ data: { stopCount: 1 } }),
    getCrowdHint: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

import MapPage from '../../pages/MapPage';

describe('MapPage', () => {
  it('renders map page successfully with async data', async () => {
    // 🟢 Wrap in act for async state updates in MapPage
    await act(async () => {
      render(
        <BrowserRouter>
          <MapPage />
        </BrowserRouter>
      );
    });

    // 🟢 Use waitFor to allow "Loading route..." to finish
    await waitFor(() => {
      expect(screen.getByText(/Explore Map/i)).toBeInTheDocument();
      expect(screen.getByTestId('map-container')).toBeInTheDocument();
      // 🟢 Use getAllByText since "Place 1" appears in both Map Popup and Sidebar list
      const placeElements = screen.getAllByText(/Place 1/i);
      expect(placeElements.length).toBeGreaterThan(0);
    }, { timeout: 4000 });
  });
});
