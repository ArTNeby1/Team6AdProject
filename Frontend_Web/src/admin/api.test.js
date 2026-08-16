import { describe, it, expect, beforeEach, vi } from 'vitest';
import { apiFetch, ADMIN_TOKEN_KEY } from './api';

// Builds a minimal fetch Response stand-in (only what apiFetch reads).
function mockResponse({ ok = true, status = 200, body = null } = {}) {
  return { ok, status, text: async () => (body == null ? '' : JSON.stringify(body)) };
}

describe('apiFetch', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('attaches the admin bearer token when one is stored', async () => {
    localStorage.setItem(ADMIN_TOKEN_KEY, 'tok123');
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({ body: { ok: true } }));
    global.fetch = fetchMock;

    await apiFetch('/api/v1/admin/users');

    const [url, opts] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/admin/users');
    expect(opts.headers.Authorization).toBe('Bearer tok123');
  });

  it('omits the auth header when auth:false, and serializes the body', async () => {
    localStorage.setItem(ADMIN_TOKEN_KEY, 'tok123');
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({ body: {} }));
    global.fetch = fetchMock;

    await apiFetch('/api/v1/admin/auth/login', {
      method: 'POST',
      body: { email: 'a@b.c' },
      auth: false,
    });

    const [, opts] = fetchMock.mock.calls[0];
    expect(opts.headers.Authorization).toBeUndefined();
    expect(opts.method).toBe('POST');
    expect(opts.body).toBe(JSON.stringify({ email: 'a@b.c' }));
  });

  it('returns parsed JSON on a successful response', async () => {
    global.fetch = vi.fn().mockResolvedValue(mockResponse({ body: { totalElements: 7 } }));
    const data = await apiFetch('/x');
    expect(data).toEqual({ totalElements: 7 });
  });

  it('throws an Error carrying status and code on a non-2xx response', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      mockResponse({ ok: false, status: 401, body: { message: 'bad', code: 'INVALID_CREDENTIALS' } }),
    );
    await expect(apiFetch('/x')).rejects.toMatchObject({
      message: 'bad',
      status: 401,
      code: 'INVALID_CREDENTIALS',
    });
  });
});
