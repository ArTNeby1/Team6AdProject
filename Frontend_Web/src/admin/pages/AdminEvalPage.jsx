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

export default function AdminEvalPage() {
  const [records, setRecords] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

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

  const averages = useMemo(() => {
    if (records.length === 0) return null;
    const sum = records.reduce((acc, rec) => ({
      precision: acc.precision + rec.precision,
      recall: acc.recall + rec.recall,
      f1: acc.f1 + rec.f1,
      groundedness: acc.groundedness + rec.groundedness,
    }), { precision: 0, recall: 0, f1: 0, groundedness: 0 });
    return {
      precision: sum.precision / records.length,
      recall: sum.recall / records.length,
      f1: sum.f1 / records.length,
      groundedness: sum.groundedness / records.length,
    };
  }, [records]);

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

            <div className="admin-eval-panel" style={{ marginTop: 20 }}>
              <h2 className="admin-eval-h2">Per-import scores — open one to inspect</h2>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Time</th><th>User</th><th>Places</th>
                      <th>Precision</th><th>Recall</th><th>F1</th><th>Grounded</th><th />
                    </tr>
                  </thead>
                  <tbody>
                    {records.map((rec) => (
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
            </div>
          </>
        ) : (
          <p>No successful imports have been recorded yet. Run an import in the app, then refresh.</p>
        )
      )}
    </div>
  );
}
