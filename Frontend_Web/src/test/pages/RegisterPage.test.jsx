import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import RegisterPage from '../../pages/RegisterPage';

// 🟢 Define mocks outside
const mockRegister = vi.fn();
const mockNavigate = vi.fn();

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    register: mockRegister,
  }),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    Link: ({ children, to }) => <a href={to}>{children}</a>,
  };
});

describe('RegisterPage', () => {
  it('renders register form correctly', () => {
    render(
      <BrowserRouter>
        <RegisterPage />
      </BrowserRouter>
    );

    expect(screen.getByText(/Create Account/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/Your name or nickname/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/Your primary email/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/Age/i)).toBeDefined();
    expect(screen.getByPlaceholderText(/At least 8 characters/i)).toBeDefined();
    expect(screen.getByRole('button', { name: /Register/i })).toBeDefined();
  });

  it('calls register and navigates on success', async () => {
    mockRegister.mockResolvedValueOnce();

    render(
      <BrowserRouter>
        <RegisterPage />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByPlaceholderText(/Your name or nickname/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByPlaceholderText(/Your primary email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByPlaceholderText(/Age/i), { target: { value: '25' } });
    fireEvent.change(screen.getByPlaceholderText(/At least 8 characters/i), { target: { value: 'password123' } });

    fireEvent.click(screen.getByRole('button', { name: /Register/i }));

    await waitFor(() => {
      expect(mockRegister).toHaveBeenCalledWith('testuser', 'test@example.com', 'password123', 25, 'Male');
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });
  });
});
