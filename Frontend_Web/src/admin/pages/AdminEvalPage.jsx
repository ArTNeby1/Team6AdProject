import React, { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { apiFetch } from '../api';

// LLM Evaluation (S2): content-level accuracy for the /extract agent, computed live over
// real imports. The backend runs an LLM-as-judge per import (derive the true "gold" places
// from the source text) and scores the agent's extraction against it — Precision / Recall /
// F1 / Groundedness. This page averages those across all imports and lets you drill into any
// single import to see exactly which places were matched, missed, or invented.
//
// The point it makes is the same as the old static demo: schema validation (Pydantic) proves
// the JSON is well-formed, but only content-level metrics reveal whether the model actually
// captured what the traveller wrote.

function pct(value) {
  return value == null ? '—' : `${Math.round(value * 100)}%`;
}

// Green / amber / red by score, so the scorecard reads at a glance.
function toneClass(value) {
  if (value == null) return '';
  if (value >= 0.8) return 'admin-stat-value--good';
  if (value >= 0.5) return 'admin-stat-value--warn';
  return 'admin-stat-value--bad';
}

const METRICS = [
  { key: 'precision', label: 'Precision', note: 'extracted places that are correct' },
  { key: 'recall', label: 'Recall', note: 'real places the model found' },
  { key: 'f1', label: 'F1 Score', note: 'precision × recall balance' },
  { key: 'groundedness', label: 'Groundedness', note: 'extracted places actually in the text' },
];

function ScoreCards({ source, className }) {
  return (
    <div className={`admin-card-grid ${className || ''}`}>
      {METRICS.map(({ key, label, note }) => (
        <div className="admin-stat-card" key={key}>
          <span className="admin-stat-label">{label}</span>
          <span className={`admin-stat-value ${toneClass(source?.[key])}`}>{pct(source?.[key])}</span>
          <span className="admin-eval-note">{note}</span>
        </div>
      ))}
    </div>
  );
}

ScoreCards.propTypes = {
  source: PropTypes.object,
  className: PropTypes.string,
};

function Chips({ names, kind }) {
  if (!names || names.length === 0) return <span className="admin-eval-note">none</span>;
  return (
    <div className="admin-eval-chips">
      {names.map((name) => (
        <span key={name} className={`admin-eval-chip ${kind === 'miss' ? 'is-miss' : 'is-hit'}`}>
          {kind === 'miss' ? '✗ ' : '✓ '}
          {name}
        </span>
      ))}
    </div>
  );
}

Chips.propTypes = {
  names: PropTypes.arrayOf(PropTypes.string),
  kind: PropTypes.oneOf(['hit', 'miss']),
};

export default function AdminEvalPage() {
  const [summary, setSummary] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    apiFetch('/api/v1/admin/agent-validations/evaluations?limit=50')
      .then((data) => {
        if (cancelled) return;
        setSummary(data);
        // Keep the open drill-down pointed at the same record after a refetch.
        setSelected((current) => (current
          ? (data.records || []).find((r) => r.id === current.id) || null
          : null));
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || 'Could not load evaluation records.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const records = summary?.records || [];

  return (
    <div>
      <div className="admin-page-head">
        <h1>LLM Evaluation</h1>
        <p className="admin-page-sub">
          Content-level accuracy for the <code>/extract</code> agent, scored live against an
          LLM-as-judge over real imports.
        </p>
      </div>

      {error && <p className="admin-alert">{error}</p>}
      {loading && <p>Scoring imports…</p>}

      {!loading && !error && (
        <>
          {summary?.scoredCount > 0 ? (
            <>
              <h2 className="admin-eval-h2">
                Average across {summary.scoredCount} import{summary.scoredCount === 1 ? '' : 's'}
              </h2>
              <ScoreCards source={summary.averages} />
            </>
          ) : (
            <p>
              {summary?.totalCount > 0
                ? 'Imports found, but the evaluation service is unavailable — no scores yet.'
                : 'No imports have been recorded yet. Run an import in the app, then refresh.'}
            </p>
          )}

          {records.length > 0 && (
            <div className="admin-eval-panel" style={{ marginTop: 20 }}>
              <h2 className="admin-eval-h2">Per-import scores — click a row to inspect</h2>
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
                        <td>{rec.predictedPlaces?.length ?? 0}</td>
                        <td>{pct(rec.precision)}</td>
                        <td>{pct(rec.recall)}</td>
                        <td>{pct(rec.f1)}</td>
                        <td>{pct(rec.groundedness)}</td>
                        <td>
                          <button className="admin-btn admin-btn-ghost" onClick={() => setSelected(rec)}>
                            Inspect
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {selected && (
            <div style={{ marginTop: 24 }}>
              <h2 className="admin-eval-h2">
                Import #{selected.id} · {selected.userEmail}
              </h2>
              {!selected.available && (
                <p className="admin-alert">
                  The evaluation service was unavailable for this import — scores below are blank.
                </p>
              )}
              <ScoreCards source={selected} />

              <div className="admin-eval-io" style={{ marginTop: 16 }}>
                <div className="admin-eval-panel">
                  <h2 className="admin-eval-h2">① Source text the traveller imported</h2>
                  <div className="admin-eval-src">{selected.sourceText || '(empty)'}</div>
                </div>
                <div className="admin-eval-panel">
                  <h2 className="admin-eval-h2">② Places the agent extracted ({selected.predictedPlaces?.length ?? 0})</h2>
                  <pre className="admin-eval-json">{JSON.stringify(selected.predictedPlaces || [], null, 2)}</pre>
                </div>
              </div>

              <div className="admin-eval-panel" style={{ marginTop: 16 }}>
                <h2 className="admin-eval-h2">
                  Gold places (LLM judge found {selected.goldPlaces?.length ?? 0}) — matched vs missed
                </h2>
                <p className="admin-eval-note" style={{ marginBottom: 6 }}>Matched — real places the agent captured</p>
                <Chips names={selected.matched} kind="hit" />
                <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Missed — real places the agent dropped</p>
                <Chips names={selected.missed} kind="miss" />
                {selected.spurious?.length > 0 && (
                  <>
                    <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Invented — extracted but not in the gold set</p>
                    <Chips names={selected.spurious} kind="miss" />
                  </>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
