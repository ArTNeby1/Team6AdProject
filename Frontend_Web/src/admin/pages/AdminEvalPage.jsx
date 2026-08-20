import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api';
import { fetchScoredImports, pct } from '../evalScoring';
import { ScoreCards } from '../EvalScoreCards';

// LLM Evaluation (S2): content-level accuracy for the /extract agent, scored live over real
// imports — entirely in the browser (see evalScoring.js). This page reads the admin
// agent-validation audit log, scores every successful import, averages the four metrics, and
// links each import to its own detail page (/admin/eval/:id).
//
// The point it makes: schema validation (Pydantic) proves the JSON is well-formed, but only
// content-level metrics reveal whether the model captured what the traveller actually wrote.

const PAGE_SIZE_OPTIONS = [5, 10, 20, 50];

export default function AdminEvalPage() {
  const [records, setRecords] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[0]);
  const [page, setPage] = useState(0); // 0-indexed

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    fetchScoredImports(apiFetch)
      .then((imports) => { if (!cancelled) setRecords(imports); })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || 'Could not load evaluation records.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // P/R/F1 average only over imports we could actually score (a reference was detected);
  // otherwise unscoreable imports would drag the average toward 0. Groundedness averages over
  // all imports, since it never needs a reference.
  const averages = useMemo(() => {
    if (records.length === 0) return null;
    const avg = (rows, key) => (rows.length
      ? rows.reduce((sum, rec) => sum + rec[key], 0) / rows.length
      : null);
    const scored = records.filter((rec) => rec.scored);
    return {
      scoredCount: scored.length,
      precision: avg(scored, 'precision'),
      recall: avg(scored, 'recall'),
      f1: avg(scored, 'f1'),
      groundedness: avg(records, 'groundedness'),
    };
  }, [records]);

  // Client-side pagination of the per-import table. Averages above always cover ALL records;
  // only the table below is paged. `safePage` clamps in case the page size grew past the last page.
  const total = records.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const safePage = Math.min(page, pageCount - 1);
  const start = safePage * pageSize;
  const visible = records.slice(start, start + pageSize);

  function changePageSize(next) {
    setPageSize(next);
    setPage(0); // a new page size invalidates the current offset — jump back to the first page
  }

  return (
    <div>
      <div className="admin-page-head">
        <h1>LLM Evaluation</h1>
        <p className="admin-page-sub">
          Content-level accuracy for the <code>/extract</code> agent, scored live over real imports.
        </p>
      </div>

      {error && <p className="admin-alert">{error}</p>}
      {loading && <p>Scoring imports…</p>}

      {!loading && !error && (
        records.length > 0 ? (
          <>
            <h2 className="admin-eval-h2">
              Average across {records.length} import{records.length === 1 ? '' : 's'}
            </h2>
            <ScoreCards source={averages} />
            {averages && averages.scoredCount < records.length && (
              <p className="admin-eval-note" style={{ marginTop: 8 }}>
                Precision / Recall / F1 averaged over {averages.scoredCount} of {records.length} imports
                where a reference place list could be detected; the rest show N/A. Groundedness covers all.
              </p>
            )}

            <div className="admin-eval-panel" style={{ marginTop: 20 }}>
              <div className="admin-eval-pager-head">
                <h2 className="admin-eval-h2" style={{ margin: 0 }}>Per-import scores — open one to inspect</h2>
                <label className="admin-eval-pagesize">
                  Rows per page:
                  <select
                    aria-label="Rows per page"
                    value={pageSize}
                    onChange={(e) => changePageSize(Number(e.target.value))}
                  >
                    {PAGE_SIZE_OPTIONS.map((n) => <option key={n} value={n}>{n}</option>)}
                  </select>
                  <span className="admin-eval-note">of {total}</span>
                </label>
              </div>

              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Time</th><th>User</th><th>Places</th>
                      <th>Precision</th><th>Recall</th><th>F1</th><th>Grounded</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((rec) => (
                      <tr key={rec.id}>
                        <td>{rec.createdAt ? new Date(rec.createdAt).toLocaleString() : '—'}</td>
                        <td>{rec.userEmail}</td>
                        <td>{rec.predicted.length}</td>
                        <td>{pct(rec.precision)}</td>
                        <td>{pct(rec.recall)}</td>
                        <td>{pct(rec.f1)}</td>
                        <td>{pct(rec.groundedness)}</td>
                        <td>
                          <Link className="admin-btn admin-btn-ghost" to={`/admin/eval/${rec.id}`}>
                            Inspect
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="admin-eval-pager">
                <span className="admin-eval-note">
                  {total === 0 ? '0' : `${start + 1}–${Math.min(start + pageSize, total)}`} of {total}
                </span>
                <div className="admin-eval-pager-btns">
                  <button
                    className="admin-btn admin-btn-ghost"
                    disabled={safePage === 0}
                    onClick={() => setPage(safePage - 1)}
                  >
                    Previous
                  </button>
                  <span className="admin-eval-note">Page {safePage + 1} / {pageCount}</span>
                  <button
                    className="admin-btn admin-btn-ghost"
                    disabled={safePage >= pageCount - 1}
                    onClick={() => setPage(safePage + 1)}
                  >
                    Next
                  </button>
                </div>
              </div>
            </div>
          </>
        ) : (
          <p>No successful imports have been recorded yet. Run an import in the app, then refresh.</p>
        )
      )}
    </div>
  );
}
