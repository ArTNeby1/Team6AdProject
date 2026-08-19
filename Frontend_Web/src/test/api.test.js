import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiFetch, TRAVELER_TOKEN_KEY, getTravelerToken, checkHealth } from '../api';

// Builds a minimal fetch Response stand-in
function mockResponse({ ok = true, status = 200, body = null } = {}) {
  return { ok, status, text: async () => (body == null ? '' : JSON.stringify(body)), json: async () => body };
}

describe('Root api.js', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('getTravelerToken returns token from localStorage', () => {
    localStorage.setItem(TRAVELER_TOKEN_KEY, 'abc');
    expect(getTravelerToken()).toBe('abc');
  });

  it('apiFetch attaches authorization header', async () => {
    localStorage.setItem(TRAVELER_TOKEN_KEY, 'tok123');
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({ body: { success: true } }));
    globalThis.fetch = fetchMock;

    await apiFetch('/api/v1/test');

    const [, opts] = fetchMock.mock.calls[0];
    expect(opts.headers.Authorization).toBe('Bearer tok123');
  });

  it('checkHealth calls health endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({ body: { status: 'UP' } }));
    globalThis.fetch = fetchMock;

    const data = await checkHealth();
    expect(data.status).toBe('UP');
  });
});
