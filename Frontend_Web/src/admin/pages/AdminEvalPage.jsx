import React, { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { apiFetch } from '../api';

// LLM Evaluation (S2): content-level accuracy for the /extract agent, computed live over real
// imports — entirely in the browser. It reads the admin agent-validation audit log (each import
// stores the traveller's source text + the places the agent extracted) and scores every import
// for Precision / Recall / F1 / Groundedness, then averages them and lets you drill into any one.
//
// The reference ("gold") list of real places is derived from the source text with a lightweight
// heuristic (capitalised multi-word phrases). That keeps this page dependency-free — no extra
// backend/ML endpoint — at the cost of an approximate P/R/F1. Groundedness needs no gold and is
// always exact (it just checks each extracted place actually appears in the source text).
//
// The point it makes is the same as before: schema validation (Pydantic) proves the JSON is
// well-formed, but only content-level metrics reveal whether the model captured what was written.

// ---- scoring (JS port of ML/eval/evaluate_extraction.py) ----

function normalize(name) {
  return (name || '').toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim();
}

function matches(a, b) {
  const na = normalize(a);
  const nb = normalize(b);
  if (!na || !nb) return false;
  return na === nb || na.includes(nb) || nb.includes(na);
}

// Heuristic gold: capitalised multi-word phrases ("Merlion Park", "Marina Bay Sands"), deduped.
// Rough, but needs no model — the honest limitation of the frontend-only approach.
function heuristicGold(sourceText) {
  const found = (sourceText || '').match(/\b[A-Z][a-zA-Z0-9]+(?: [A-Z][a-zA-Z0-9]+){1,4}\b/g) || [];
  const seen = new Set();
  const gold = [];
  found.forEach((phrase) => {
    const key = normalize(phrase);
    if (key && !seen.has(key)) {
      seen.add(key);
      gold.push(phrase);
    }
  });
  return gold;
}

function scoreImport(sourceText, predictedRaw) {
  const predicted = (predictedRaw || []).filter((p) => normalize(p));
  const gold = heuristicGold(sourceText);

  const matched = gold.filter((g) => predicted.some((p) => matches(g, p)));
  const missed = gold.filter((g) => !matched.includes(g));
  const spurious = predicted.filter((p) => !gold.some((g) => matches(g, p)));

  const precision = predicted.length ? (predicted.length - spurious.length) / predicted.length : 0;
  const recall = gold.length ? matched.length / gold.length : 0;
  const f1 = precision + recall ? (2 * precision * recall) / (precision + recall) : 0;

  const srcNorm = normalize(sourceText);
  const grounded = predicted.filter((p) => srcNorm.includes(normalize(p)));
  const groundedness = predicted.length ? grounded.length / predicted.length : 0;

  return { precision, recall, f1, groundedness, gold, predicted, matched, missed, spurious };
}

// The audit log stores payloads as JSON strings; pull the source text + extracted place names.
function parseImport(record) {
  let sourceText = '';
  let predicted = [];
  try {
    sourceText = JSON.parse(record.requestPayload)?.raw_content || '';
  } catch { /* leave blank on malformed payload */ }
  try {
    const places = JSON.parse(record.responsePayload)?.places || [];
    predicted = places
      .map((place) => (place && place.name ? String(place.name).trim() : ''))
      .filter(Boolean);
  } catch { /* leave empty on malformed payload */ }

  const metrics = scoreImport(sourceText, predicted);
  return {
    id: record.id,
    userEmail: record.userEmail,
    createdAt: record.createdAt,
    sourceText,
    ...metrics,
  };
}

// ---- presentation ----

function pct(value) {
  return value == null ? '—' : `${Math.round(value * 100)}%`;
}

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

function ScoreCards({ source }) {
  return (
    <div className="admin-card-grid">
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

ScoreCards.propTypes = { source: PropTypes.object };

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
  const [records, setRecords] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    // Read the existing admin audit log; keep successful imports (they carry a comparable
    // extraction — REFINE / FAILED rows do not) and score them client-side.
    apiFetch('/api/v1/admin/agent-validations?page=0&size=50')
      .then((data) => {
        if (cancelled) return;
        const imports = (data.content || [])
          .filter((row) => row.operation === 'IMPORT' && row.outcome === 'SUCCESS')
          .map(parseImport);
        setRecords(imports);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || 'Could not load evaluation records.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
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

  const selected = records.find((rec) => rec.id === selectedId) || null;

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
        <>
          {records.length > 0 ? (
            <>
              <h2 className="admin-eval-h2">
                Average across {records.length} import{records.length === 1 ? '' : 's'}
              </h2>
              <ScoreCards source={averages} />

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
                          <td>{rec.predicted.length}</td>
                          <td>{pct(rec.precision)}</td>
                          <td>{pct(rec.recall)}</td>
                          <td>{pct(rec.f1)}</td>
                          <td>{pct(rec.groundedness)}</td>
                          <td>
                            <button className="admin-btn admin-btn-ghost" onClick={() => setSelectedId(rec.id)}>
                              Inspect
                            </button>
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
          )}

          {selected && (
            <div style={{ marginTop: 24 }}>
              <h2 className="admin-eval-h2">Import #{selected.id} · {selected.userEmail}</h2>
              <ScoreCards source={selected} />

              <div className="admin-eval-io" style={{ marginTop: 16 }}>
                <div className="admin-eval-panel">
                  <h2 className="admin-eval-h2">① Source text the traveller imported</h2>
                  <div className="admin-eval-src">{selected.sourceText || '(empty)'}</div>
                </div>
                <div className="admin-eval-panel">
                  <h2 className="admin-eval-h2">② Places the agent extracted ({selected.predicted.length})</h2>
                  <pre className="admin-eval-json">{JSON.stringify(selected.predicted, null, 2)}</pre>
                </div>
              </div>

              <div className="admin-eval-panel" style={{ marginTop: 16 }}>
                <h2 className="admin-eval-h2">
                  Reference places (heuristic found {selected.gold.length}) — matched vs missed
                </h2>
                <p className="admin-eval-note" style={{ marginBottom: 6 }}>Matched — real places the agent captured</p>
                <Chips names={selected.matched} kind="hit" />
                <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Missed — real places the agent dropped</p>
                <Chips names={selected.missed} kind="miss" />
                {selected.spurious.length > 0 && (
                  <>
                    <p className="admin-eval-note" style={{ margin: '12px 0 6px' }}>Not in reference — extracted but not a heuristic place</p>
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
