import React, { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../api';

const PAGE_SIZE = 20;

function formatDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

// Read-only traveler list (S1): server-side pagination + email search.
export default function AdminUsersPage() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');       // committed search term
  const [queryInput, setQueryInput] = useState(''); // live input box
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (query) params.set('q', query);
      const result = await apiFetch(`/api/v1/admin/users?${params.toString()}`);
      setData(result);
    } catch (err) {
      setError(err.message || 'Failed to load users.');
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [page, query]);

  useEffect(() => { load(); }, [load]);

  function submitSearch(e) {
    e.preventDefault();
    setPage(0);
    setQuery(queryInput.trim());
  }

  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const rows = data?.content ?? [];

  return (
    <div>
      <div className="admin-page-head">
        <h1>Users</h1>
        <p className="admin-page-sub">Read-only view of traveler accounts</p>
      </div>

      <form className="admin-toolbar" onSubmit={submitSearch}>
        <input
          className="admin-search"
          type="search"
          placeholder="Search by email…"
          value={queryInput}
          onChange={(e) => setQueryInput(e.target.value)}
        />
        <button className="admin-btn admin-btn-primary" type="submit">Search</button>
        {query && (
          <button
            type="button"
            className="admin-btn admin-btn-ghost"
            onClick={() => { setQueryInput(''); setQuery(''); setPage(0); }}
          >
            Clear
          </button>
        )}
        <span className="admin-toolbar-count">{totalElements} total</span>
      </form>

      {error && <div className="admin-alert" role="alert">{error}</div>}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th style={{ width: '72px' }}>ID</th>
              <th>Email</th>
              <th style={{ width: '80px' }}>Age</th>
              <th style={{ width: '110px' }}>Gender</th>
              <th style={{ width: '120px' }}>Role</th>
              <th style={{ width: '200px' }}>Created</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="admin-table-empty">Loading…</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={6} className="admin-table-empty">No users found.</td></tr>
            ) : (
              rows.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.email}</td>
                  <td>{u.age ?? '—'}</td>
                  <td>{u.gender ?? '—'}</td>
                  <td><span className={`admin-role-badge admin-role-${u.role}`}>{u.role}</span></td>
                  <td>{formatDate(u.createdAt)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="admin-pagination">
        <button
          className="admin-btn admin-btn-ghost"
          disabled={loading || page <= 0}
          onClick={() => setPage((p) => Math.max(p - 1, 0))}
        >
          ← Prev
        </button>
        <span className="admin-pagination-info">
          Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
        </span>
        <button
          className="admin-btn admin-btn-ghost"
          disabled={loading || page + 1 >= totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          Next →
        </button>
      </div>
    </div>
  );
}
