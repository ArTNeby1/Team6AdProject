import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const updateTripDate = vi.fn();

const baseTrip = {
  id: '44',
  title: 'E2E date check',
  date: '2026-08-19',
  status: 'NOT_STARTED',
  dayCount: 1,
  locations: [],
};

let currentTrip = baseTrip;

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({ id: '44' }),
  useLocation: () => ({ state: null }),
}));

vi.mock('../services/api', () => ({
  mapApi: { getRoute: vi.fn().mockResolvedValue({ data: {} }) },
}));

vi.mock('../context/TripContext', () => ({
  useTrip: () => ({
    getTripById: () => currentTrip,
    getActiveTrip: () => currentTrip,
    setActiveTripId: vi.fn(),
    addDayToTrip: vi.fn(),
    addLocationsToTripDay: vi.fn(),
    updateTripTitle: vi.fn(),
    updateTripDate,
    loadingTrips: false,
  }),
}));

const { default: ItineraryDetailPage } = await import('../pages/ItineraryDetailPage');

/**
 * 这组测试盯的是"点了没反应"那个 bug（2026-08-19 修）：
 * 以前的实现是把一个 opacity:0 的 <input type="date"> 铺满整颗胶囊，指望用户点中
 * Chrome 的 ::-webkit-calendar-picker-indicator。那个指示器落在胶囊右侧 padding 里、
 * 在可见的 📅 emoji 右边，用户点图标其实点不到它 —— 于是什么都不会发生。
 * 现在改成点整块 -> 调 input.showPicker()，所以测试直接断言 showPicker 被调用。
 */
describe('ItineraryDetailPage start date picker', () => {
  beforeEach(() => {
    currentTrip = baseTrip;
    updateTripDate.mockClear();
  });

  const renderAndGetPill = () => {
    const { container } = render(<ItineraryDetailPage />);
    return {
      container,
      pill: container.querySelector('.trip-date-modifier'),
      input: container.querySelector('.trip-date-modifier input[type="date"]'),
    };
  };

  it('opens the native picker when the whole pill is clicked, not just the calendar icon', () => {
    const { pill, input } = renderAndGetPill();
    const showPicker = vi.fn();
    input.showPicker = showPicker;

    // 点图标（以前这里必然没反应）
    fireEvent.click(screen.getByText('📅'));
    expect(showPicker).toHaveBeenCalledTimes(1);

    // 点文字标签，同样要能打开
    fireEvent.click(screen.getByText('Start Time:'));
    expect(showPicker).toHaveBeenCalledTimes(2);

    // 点胶囊本身
    fireEvent.click(pill);
    expect(showPicker).toHaveBeenCalledTimes(3);
  });

  it('opens the picker from the keyboard (Enter / Space)', () => {
    const { pill, input } = renderAndGetPill();
    const showPicker = vi.fn();
    input.showPicker = showPicker;

    fireEvent.keyDown(pill, { key: 'Enter' });
    fireEvent.keyDown(pill, { key: ' ' });
    expect(showPicker).toHaveBeenCalledTimes(2);

    expect(pill.getAttribute('role')).toBe('button');
    expect(pill.getAttribute('tabindex')).toBe('0');
  });

  it('falls back to focus() instead of throwing when showPicker is unavailable', () => {
    const { pill, input } = renderAndGetPill();
    input.showPicker = undefined;
    const focus = vi.spyOn(input, 'focus');

    expect(() => fireEvent.click(pill)).not.toThrow();
    expect(focus).toHaveBeenCalled();
  });

  it('swallows a showPicker error rather than surfacing it to the user', () => {
    const { pill, input } = renderAndGetPill();
    input.showPicker = vi.fn(() => {
      throw new Error('InvalidStateError');
    });
    const focus = vi.spyOn(input, 'focus');

    expect(() => fireEvent.click(pill)).not.toThrow();
    expect(focus).toHaveBeenCalled();
  });

  it('persists the picked date through updateTripDate', () => {
    const { input } = renderAndGetPill();
    fireEvent.change(input, { target: { value: '2026-09-01' } });
    expect(updateTripDate).toHaveBeenCalledWith('44', '2026-09-01');
  });

  it('blocks past dates via the input min attribute', () => {
    const { input } = renderAndGetPill();
    expect(input.getAttribute('min')).toBe(new Date().toISOString().split('T')[0]);
  });

  it('renders no picker at all for a FINISHED trip', () => {
    currentTrip = { ...baseTrip, status: 'FINISHED' };
    const { pill, input } = renderAndGetPill();
    expect(input).toBeNull();
    expect(pill.getAttribute('role')).toBeNull();
    expect(() => fireEvent.click(pill)).not.toThrow();
  });
});
