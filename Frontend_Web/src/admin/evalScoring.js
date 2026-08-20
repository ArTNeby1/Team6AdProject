// Content-level accuracy scoring for the LLM Evaluation console, computed in the browser.
// Shared by the list page (AdminEvalPage) and the single-import detail page
// (AdminEvalDetailPage) so both score identically. JS port of
// ML/eval/evaluate_extraction.py.
//
// The reference ("gold") list of real places is derived from the source text with a
// capitalised-phrase heuristic — no model, no extra backend/ML endpoint. So Precision / Recall
// / F1 are approximate; Groundedness needs no gold and is always exact (it just checks each
// extracted place actually appears in the source text).

export function normalize(name) {
  return (name || '').toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim();
}

export function matches(a, b) {
  const na = normalize(a);
  const nb = normalize(b);
  if (!na || !nb) return false;
  return na === nb || na.includes(nb) || nb.includes(na);
}

// Capitalised multi-word phrases ("Merlion Park", "Marina Bay Sands"), deduped by normalized form.
export function heuristicGold(sourceText) {
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

export function scoreImport(sourceText, predictedRaw) {
  const predicted = (predictedRaw || []).filter((p) => normalize(p));
  const gold = heuristicGold(sourceText);

  // Precision / Recall / F1 all need a reference ("gold") list. The heuristic only detects
  // capitalised multi-word phrases, so casual / lowercase source text ("sentosa and arab
  // street") yields no gold — and then these three are *unscoreable*, not zero. Reporting 0%
  // there would wrongly read as "the model got everything wrong". So we mark them null (shown
  // as N/A). Groundedness needs no gold and is always computed.
  const scored = gold.length > 0;

  const matched = gold.filter((g) => predicted.some((p) => matches(g, p)));
  const missed = gold.filter((g) => !matched.includes(g));
  const spurious = scored ? predicted.filter((p) => !gold.some((g) => matches(g, p))) : [];

  let precision = null;
  let recall = null;
  let f1 = null;
  if (scored) {
    precision = predicted.length ? (predicted.length - spurious.length) / predicted.length : 0;
    recall = matched.length / gold.length;
    f1 = precision + recall ? (2 * precision * recall) / (precision + recall) : 0;
  }

  const srcNorm = normalize(sourceText);
  const grounded = predicted.filter((p) => srcNorm.includes(normalize(p)));
  const groundedness = predicted.length ? grounded.length / predicted.length : 0;

  return { scored, precision, recall, f1, groundedness, gold, predicted, matched, missed, spurious };
}

// The audit log stores payloads as JSON strings; pull the source text + extracted place names,
// then score. Returns a flat record ready for display.
export function parseImport(record) {
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

  return {
    id: record.id,
    userEmail: record.userEmail,
    createdAt: record.createdAt,
    sourceText,
    ...scoreImport(sourceText, predicted),
  };
}

// Fetch the admin audit log and return every successful import, scored. Shared fetch path so the
// list and detail pages read the same data. REFINE / FAILED rows carry no comparable extraction.
export async function fetchScoredImports(apiFetch) {
  const data = await apiFetch('/api/v1/admin/agent-validations?page=0&size=50');
  return (data.content || [])
    .filter((row) => row.operation === 'IMPORT' && row.outcome === 'SUCCESS')
    .map(parseImport);
}

export const METRICS = [
  { key: 'precision', label: 'Precision', note: 'extracted places that are correct' },
  { key: 'recall', label: 'Recall', note: 'real places the model found' },
  { key: 'f1', label: 'F1 Score', note: 'precision × recall balance' },
  { key: 'groundedness', label: 'Groundedness', note: 'extracted places actually in the text' },
];

export function pct(value) {
  return value == null ? 'N/A' : `${Math.round(value * 100)}%`;
}

export function toneClass(value) {
  if (value == null) return '';
  if (value >= 0.8) return 'admin-stat-value--good';
  if (value >= 0.5) return 'admin-stat-value--warn';
  return 'admin-stat-value--bad';
}
