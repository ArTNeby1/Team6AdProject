// Traveler API client (separate token from admin console).
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const TRAVELER_TOKEN_KEY = 'loomytrip_token';

export function getTravelerToken() {
  return localStorage.getItem(TRAVELER_TOKEN_KEY);
}

export async function checkHealth() {
  const res = await fetch(`${API_BASE}/api/v1/health`);
  if (!res.ok) {
    throw new Error(`Health check failed (${res.status})`);
  }
  return res.json();
}

/**
 * Calls the backend and returns parsed JSON (or null for empty bodies).
 * Attaches the traveler bearer token unless `auth: false` is passed.
 * Throws an Error carrying { status, code } so callers can branch on it.
 */
export async function apiFetch(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getTravelerToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const err = new Error(data?.message || `Request failed (${res.status})`);
    err.status = res.status;
    err.code = data?.code;
    throw err;
  }
  return data;
}
