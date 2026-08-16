import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AdminEvalPage from './AdminEvalPage';

// AdminEvalPage is a self-contained static page (no context/props), so it can be
// rendered directly.
describe('AdminEvalPage', () => {
  it('renders the four accuracy metric cards', () => {
    render(<AdminEvalPage />);
    expect(screen.getByText('Precision')).toBeInTheDocument();
    expect(screen.getByText('Recall')).toBeInTheDocument();
    expect(screen.getByText('F1 Score')).toBeInTheDocument();
    expect(screen.getByText('Groundedness')).toBeInTheDocument();
  });

  it('marks the 2 extracted places as hits and the other 7 as misses', () => {
    const { container } = render(<AdminEvalPage />);
    expect(container.querySelectorAll('.admin-eval-chip.is-hit')).toHaveLength(2);
    expect(container.querySelectorAll('.admin-eval-chip.is-miss')).toHaveLength(7);
  });
});
