import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

// 🟢 Ultra-minimal Leaflet mock to prevent memory bloat during coverage
vi.mock('leaflet', () => {
  const mockIcon = vi.fn(() => ({ options: {} }));
  mockIcon.Default = { prototype: { _getIconUrl: vi.fn() }, mergeOptions: vi.fn() };

  return {
    default: {
      Icon: mockIcon,
      DivIcon: vi.fn(() => ({ options: {} })),
      divIcon: vi.fn(() => ({})),
      latLng: vi.fn((lat, lng) => ({ lat, lng })),
    },
    Icon: mockIcon,
    DivIcon: vi.fn(() => ({ options: {} })),
    divIcon: vi.fn(() => ({})),
    latLng: vi.fn((lat, lng) => ({ lat, lng })),
  };
});

// Mock react-leaflet
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }) => <div data-testid="map-container">{children}</div>,
  TileLayer: () => <div data-testid="tile-layer" />,
  Marker: ({ children }) => <div data-testid="marker">{children}</div>,
  Popup: ({ children }) => <div data-testid="popup">{children}</div>,
  Polyline: () => <div data-testid="polyline" />,
  useMap: () => ({ flyTo: vi.fn(), getZoom: () => 12, setView: vi.fn() }),
  useMapEvents: () => ({}),
}));

vi.mock('leaflet.heat', () => ({ default: vi.fn() }));

vi.mock('../context/TripContext', () => ({
  useTrip: () => ({
    trips: [{ id: '1', title: 'Trip 1', dayCount: 1, locations: [{ id: '1', name: 'Place 1', day: 1, lat: 1.35, lng: 103.8 }] }],
    getActiveTrip: () => ({ id: '1', title: 'Trip 1', dayCount: 1, locations: [{ id: '1', name: 'Place 1', day: 1, lat: 1.35, lng: 103.8 }] }),
  }),
}));

vi.mock('../services/api', () => ({
  mapApi: {
    getConfig: () => Promise.resolve({ data: {} }),
    getCrowdHint: () => Promise.resolve({ data: {} }),
  },
}));

import MapPage from './MapPage';

describe('MapPage', () => {
  it('renders map page successfully', () => {
    render(
      <BrowserRouter>
        <MapPage />
      </BrowserRouter>
    );

    expect(screen.getByText(/Explore Map/i)).toBeInTheDocument();
    expect(screen.getByTestId('map-container')).toBeInTheDocument();
  });
});
