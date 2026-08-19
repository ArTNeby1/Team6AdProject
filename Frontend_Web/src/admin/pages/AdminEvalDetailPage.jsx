import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { apiFetch } from '../api';
import { fetchScoredImports } from '../evalScoring';
import { ScoreCards, Chips } from '../EvalScoreCards';

// Single-import evaluation detail (/admin/eval/:id): the four metrics for one import plus the
// full breakdown — source text, extracted output, and matched / missed / not-in-reference place
// chips. Frontend-only, so it re-reads the audit log and finds this import by id (bookmarkable /
// refresh-safe; no reliance on router state passed from the list page).

export default function AdminEvalDetailPage() {
  const { id } = useParams();
  const [record, setRecord] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    fetchScoredImports(apiFetch)
      .then((imports) => {
        if (cancelled) return;
        setRecord(imports.find((rec) => String(rec.id) === String(id)) || null);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || 'Could not load this import.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [id]);

  return (
    <div>
      <div className="admin-page-head">
        <Link className="admin-btn admin-btn-ghost" to="/admin/eval">← Back to evaluation</Link>
        <h1 style={{ marginTop: 12 }}>Import #{id}</h1>
      </div>

      {error && <p className="admin-alert">{error}</p>}
      {loading && <p>Scoring import…</p>}

      {!loading && !error && !record && (
        <p>Import #{id} was not found among the 50 most recent imports.</p>
      )}

      {!loading && !error && record && (
        <>
          <p className="admin-page-sub" style={{ marginBottom: 16 }}>
            {record.userEmail}
            {record.createdAt ? ` · ${new Date(record.createdAt).toLocaleString()}` : ''}
          </p>

          <ScoreCards source={record} />

          <div className="admin-eval-io" style={{ marginTop: 16 }}>
            <div className="admin-eval-panel">
              <h2 className="admin-eval-h2">① Source text the traveller imported</h2>
              <div className="admin-eval-src">{record.sourceText || '(empty)'}</div>
            </div>
            <div className="admin-eval-panel">
              <h2 className="admin-eval-h2">② Places the agent extracted ({record.predicted.length})</h2>
              <pre className="admin-eval-json">{JSON.stringify(record.predicted, null, 2)}</pre>
            </div>
          </div>

          <div className="admin-eval-panel" style={{ marginTop: 16 }}>
            <h2 className="admin-eval-h2">
              Reference places (heuristic found {record.gold.length}) — matched vs missed
            </h2>
            <p className="admin-eval-note" style={{ marginBottom: 6 }}>Matched — real places the agent captured</p>
            <Chips names={record.matched} kind="hit" />
            <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Missed — real places the agent dropped</p>
            <Chips names={record.missed} kind="miss" />
            {record.spurious.length > 0 && (
              <>
                <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Not in reference — extracted but not a heuristic place</p>
                <Chips names={record.spurious} kind="miss" />
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
