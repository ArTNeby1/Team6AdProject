// Thin fetch wrapper for the admin console.
// Base URL is configurable so the same build runs against local / dev / prod
// backends; it defaults to the Spring Boot dev port.
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const ADMIN_TOKEN_KEY = 'loomytrip_admin_token';

export function getAdminToken() {
  return localStorage.getItem(ADMIN_TOKEN_KEY);
}

/**
 * Calls the backend and returns parsed JSON.
 * Attaches the admin bearer token unless `auth: false` is passed (login).
 * Throws an Error carrying { status, code } so callers can branch on it.
 */
export async function apiFetch(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) {
    const token = getAdminToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
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
