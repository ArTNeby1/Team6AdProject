import React from 'react';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import EditPage from './EditPage';

// 🟢 1. 使用 hoisted 定義一個靜態、永遠不變的行程對象引用
const { mockTripStatic } = vi.hoisted(() => ({
  mockTripStatic: {
    id: '1',
    title: 'Stable Edit Trip',
    dayCount: 1,
    status: 'ACTIVE',
    locations: [
      { id: '101', name: 'Location 1', day: 1, time: '10:00' }
    ]
  }
}));

// 🟢 2. 確保 Hook 始終返回同一個對象
vi.mock('../context/TripContext', () => ({
  useTrip: () => ({
    getActiveTrip: () => mockTripStatic,
    loadingTrips: false,
    addDayToTrip: vi.fn(),
    deleteDay: vi.fn(),
    saveTripEdits: vi.fn(),
    updateTripTitle: vi.fn(),
  }),
}));

// 🟢 3. 簡化 DND Mock，移除不必要的計算
vi.mock('@hello-pangea/dnd', () => ({
  DragDropContext: ({ children }) => <div data-testid="dnd-context">{children}</div>,
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
  it('renders edit page successfully without infinite loop', () => {
    render(
      <BrowserRouter>
        <EditPage />
      </BrowserRouter>
    );

    expect(screen.getByText(/Edit Itinerary/i)).toBeInTheDocument();
    expect(screen.getByText(/Stable Edit Trip/i)).toBeInTheDocument();
    expect(screen.getByText(/Location 1/i)).toBeInTheDocument();
  });
});
