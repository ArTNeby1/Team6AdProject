import React from 'react';

// LLM Evaluation (S2): content-level accuracy scorecard for the /extract agent.
// Static demo data — mirrors the numbers produced by ML/eval/evaluate_extraction.py.
// The point it makes: schema validation (Pydantic) is necessary but not sufficient.
// The JSON below is 100% structurally valid, yet the model silently drops most of
// the places in the source text — an error only content-level metrics can catch.

const GOLD_PLACES = [
  'Gardens by the Bay',
  'Satay by the Bay',
  'Merlion Park',
  'Marina Bay Sands SkyPark',
  'Chinatown Street Market',
  'Chinatown Complex Food Centre',
  'Universal Studios Singapore',
  'Wings of Time',
  'Vivo City',
];
const EXTRACTED = ['Gardens by the Bay', 'Satay by the Bay'];

function isHit(name) {
  const n = name.toLowerCase();
  return EXTRACTED.some((e) => {
    const en = e.toLowerCase();
    return en.includes(n) || n.includes(en);
  });
}

const MODEL_OUTPUT = `{
  "destination": "Singapore",
  "dates": ["2026-09-13", "2026-09-14"],
  "places": [
    { "name": "Gardens by the Bay",
      "type": "attraction" },
    { "name": "Satay by the Bay",
      "type": "restaurant" }
  ]
}
// Passes Pydantic validation — but 7 places are missing`;

export default function AdminEvalPage() {
  return (
    <div>
      <div className="admin-page-head">
        <h1>LLM Evaluation</h1>
        <p className="admin-page-sub">
          Content-level accuracy for the <code>/extract</code> agent · sample: 3-day Singapore trip
        </p>
      </div>

      {/* Input text + model output, side by side */}
      <div className="admin-eval-io">
        <div className="admin-eval-panel">
          <h2 className="admin-eval-h2">① Input text — 9 real places (highlighted)</h2>
          <div className="admin-eval-src">
            3 Days in Singapore — My Full Itinerary{'\n\n'}
            Day 1: dropped bags near Bugis. First stop was <mark>Gardens by the Bay</mark> — Cloud
            Forest dome, then the Super Tree Grove light show. Dinner at <mark>Satay by the Bay</mark>.
            {'\n\n'}
            Day 2: morning at <mark>Merlion Park</mark>, afternoon at <mark>Marina Bay Sands SkyPark</mark>.
            Evening: <mark>Chinatown Street Market</mark>, then <mark>Chinatown Complex Food Centre</mark>.
            {'\n\n'}
            Day 3: <mark>Universal Studios Singapore</mark> on Sentosa, then <mark>Wings of Time</mark> on
            the beach. Meal at <mark>Vivo City</mark> before the flight.
          </div>
        </div>
        <div className="admin-eval-panel">
          <h2 className="admin-eval-h2">② Model output — only 2 places extracted</h2>
          <pre className="admin-eval-json">{MODEL_OUTPUT}</pre>
        </div>
      </div>

      <p className="admin-eval-flow">▼&nbsp;&nbsp;Compare output ② against the 9 real places in ①&nbsp;&nbsp;▼</p>

      {/* Scorecard */}
      <div className="admin-card-grid">
        <div className="admin-stat-card">
          <span className="admin-stat-label">Precision</span>
          <span className="admin-stat-value admin-stat-value--good">100%</span>
          <span className="admin-eval-note">extracted places all correct</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">Recall</span>
          <span className="admin-stat-value admin-stat-value--bad">22%</span>
          <span className="admin-eval-note">2 of 9 real places found</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">F1 Score</span>
          <span className="admin-stat-value admin-stat-value--warn">36%</span>
          <span className="admin-eval-note">precision × recall balance</span>
        </div>
        <div className="admin-stat-card">
          <span className="admin-stat-label">Groundedness</span>
          <span className="admin-stat-value admin-stat-value--good">100%</span>
          <span className="admin-eval-note">no hallucinated places</span>
        </div>
      </div>

      {/* Place-level detail */}
      <div className="admin-eval-panel" style={{ marginTop: 16 }}>
        <h2 className="admin-eval-h2">Place-level detail — 9 real places, model found 2</h2>
        <div className="admin-eval-chips">
          {GOLD_PLACES.map((name) => {
            const hit = isHit(name);
            return (
              <span key={name} className={`admin-eval-chip ${hit ? 'is-hit' : 'is-miss'}`}>
                {hit ? '✓ ' : '✗ '}
                {name}
              </span>
            );
          })}
        </div>
      </div>

      <div className="admin-eval-verdict">
        <strong>Structurally 100% valid, yet 78% of the content is missing.</strong> The JSON passes
        every Pydantic check (fields present, types correct, enums valid), but the model extracted
        only 2 of 9 places. This “valid format, missing content” error is invisible to schema
        validation — only content-level metrics (Precision / Recall / F1 + groundedness) reveal the
        agent’s true accuracy.
      </div>
    </div>
  );
}
