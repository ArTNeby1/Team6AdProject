import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import Footer from './Footer';

describe('Footer', () => {
  it('renders footer content correctly', () => {
    render(<Footer />);

    expect(screen.getAllByText(/LoomyTrip/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Explore every corner of the world/i)).toBeDefined();
    expect(screen.getByText(/Product/i)).toBeDefined();
    expect(screen.getByText(/Company/i)).toBeDefined();
    expect(screen.getByText(/Support/i)).toBeDefined();
    expect(screen.getByText(/© 2024 LoomyTrip/i)).toBeDefined();
  });

  it('contains expected links', () => {
    render(<Footer />);

    expect(screen.getByText(/AI Itinerary Planning/i)).toBeDefined();
    expect(screen.getByText(/About Us/i)).toBeDefined();
    expect(screen.getByText(/Help Center/i)).toBeDefined();
  });
});
