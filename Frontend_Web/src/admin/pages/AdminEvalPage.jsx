import React, { useEffect, useState } from 'react';
import { apiFetch } from '../api';

function prettyJson(payload) {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}

export default function AdminEvalPage() {
  const [logs, setLogs] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    apiFetch(`/api/v1/admin/agent-validations?page=${page}&size=20`)
      .then((data) => {
        if (cancelled) return;
        setLogs(data.content || []);
        setTotalPages(data.totalPages || 0);
        setSelected((current) => current && (data.content || []).find((log) => log.id === current.id));
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || 'Could not load agent validation records.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [page]);

  return (
    <div>
      <div className="admin-page-head">
        <h1>LLM Evaluation</h1>
        <p className="admin-page-sub">
          Compare the original planning request with the structured response returned by the LLM.
        </p>
      </div>
      {error && <p className="admin-error">{error}</p>}
      {loading ? <p>Loading validation records…</p> : (
        <>
          {logs.length === 0 ? <p>No LLM requests have been recorded yet.</p> : (
            <div className="admin-eval-panel">
              <h2 className="admin-eval-h2">Recent agent requests</h2>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead><tr><th>Time</th><th>Operation</th><th>User</th><th>Outcome</th><th /></tr></thead>
                  <tbody>{logs.map((log) => (
                    <tr key={log.id}>
                      <td>{log.createdAt ? new Date(log.createdAt).toLocaleString() : '—'}</td>
                      <td>{log.operation}</td><td>{log.userEmail}</td><td>{log.outcome}</td>
                      <td><button className="btn-secondary" onClick={() => setSelected(log)}>Compare</button></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </div>
          )}
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <button className="btn-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </>
      )}
      {selected && (
        <div className="admin-eval-io" style={{ marginTop: 20 }}>
          <div className="admin-eval-panel"><h2 className="admin-eval-h2">① Request sent to the agent</h2><pre className="admin-eval-json">{prettyJson(selected.requestPayload)}</pre></div>
          <div className="admin-eval-panel"><h2 className="admin-eval-h2">② LLM response returned to the backend</h2><pre className="admin-eval-json">{prettyJson(selected.responsePayload)}</pre></div>
        </div>
      )}
    </div>
  );
}
