import React from 'react';
import PropTypes from 'prop-types';
import { METRICS, pct, toneClass } from './evalScoring';

// The four-metric scorecard, shared by the list-page average and the per-import detail.
export function ScoreCards({ source }) {
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

// A row of place chips (matched = green tick, missed/spurious = red cross).
export function Chips({ names, kind }) {
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
