import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import EditPage from './EditPage';

// 🟢 Using hoisted mock to prevent reference errors during GC
const { mockTripData } = vi.hoisted(() => ({
  mockTripData: {
    getActiveTrip: () => ({
      id: '1',
      title: 'Edit Trip',
      dayCount: 1,
      locations: [
        { id: '101', name: 'Location 1', day: 1, time: '10:00' }
      ]
    }),
    loadingTrips: false,
    addDayToTrip: vi.fn(),
    deleteDay: vi.fn(),
    saveTripEdits: vi.fn(),
    updateTripTitle: vi.fn(),
  }
}));

vi.mock('../context/TripContext', () => ({
  useTrip: () => mockTripData,
}));

vi.mock('@hello-pangea/dnd', () => ({
  DragDropContext: ({ children }) => <div>{children}</div>,
  Droppable: ({ children }) => children({
    draggableProps: {},
    innerRef: vi.fn(),
    placeholder: null,
    droppableProps: {}
  }, { isDraggingOver: false }),
  Draggable: ({ children }) => children({
    draggableProps: {},
    dragHandleProps: {},
    innerRef: vi.fn()
  }, { isDragging: false }),
}));

describe('EditPage', () => {
  it('renders edit page successfully', () => {
    render(
      <BrowserRouter>
        <EditPage />
      </BrowserRouter>
    );

    expect(screen.getByText(/Edit Itinerary/i)).toBeInTheDocument();
    expect(screen.getByText(/Location 1/i)).toBeInTheDocument();
  });
});
