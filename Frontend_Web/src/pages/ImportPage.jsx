import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import api from '../services/api';

const mapDraftPlaces = (draftPlaces) =>
  draftPlaces.map((p) => ({
    id: p.id,
    name: p.name,
    selected: true,
    status: p.validationStatus === 'VALID' ? 'ok' : 'warn',
    label: p.validationStatus === 'VALID' ? 'Located' : 'Check Location',
    activities: p.activities || [],
    // Place-level suggestedDay wins (matches PlanningService.resolveSuggestedDay's own
    // precedence) — only fall back to an activity's suggestedDay for places extracted
    // before that column existed. Reading only the activity side here used to mean a
    // place with zero activities (the common case — basic extraction doesn't attach any)
    // always looked un-split even after persistPlaceDay had written the place-level field.
    day: p.suggestedDay ?? p.activities?.find((activity) => activity.suggestedDay != null)?.suggestedDay ?? null,
  }));

const ImportPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { fetchTrips, addLocationsToTripDay } = useTrip();

  const targetTripId = searchParams.get('tripId');
  const targetDay = searchParams.get('day');
  const initialText = searchParams.get('text') || "";

  const [sessionId, setSessionId] = useState(null);
  const [text, setText] = useState(initialText);

  // Sync text from URL parameter if it changes (e.g. navigating from Home with new query)
  React.useEffect(() => {
    if (initialText) {
      setText(initialText);
    }
  }, [initialText]);
  const [results, setResults] = useState([]);
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(false);
  const [refineText, setRefineText] = useState("");
  const [durationDays, setDurationDays] = useState(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importError, setImportError] = useState("");

  // Writes the place-level suggestedDay — PlanningService.resolveSuggestedDay() checks this
  // before ever looking at an activity's suggestedDay, so this is what actually needs to be
  // set for confirm() to honor the split. (Previously this only wrote activity.suggestedDay,
  // which silently did nothing for any place with zero activities — the normal case for a
  // basic-extraction place, so the "automatic day split" never actually persisted and every
  // place landed on day 1 at confirm regardless of what the review screen showed.)
  const persistPlaceDay = async (place, day) => {
    await api.put(`/planning-sessions/draft-places/${place.id}`, { suggestedDay: day });
  };

  const distributePlacesAcrossDays = async (places, days) => {
    if (!Number.isInteger(days) || days < 1 || places.length === 0) return places;

    const assignments = places.map((place, index) => ({
      ...place,
      day: Math.min(days, Math.floor((index * days) / places.length) + 1),
    }));
    await Promise.all(assignments.map((place) => persistPlaceDay(place, place.day)));
    return assignments;
  };

  const loadDraftPlaces = async (sid, validate = true, autoSplit = false) => {
    if (validate) {
      await api.post(`/planning-sessions/${sid}/validate-places`);
    }
    const response = await api.get(`/planning-sessions/${sid}`);
    const detectedDays = response.data.durationDays;
    let places = mapDraftPlaces(response.data.draftPlaces || []);
    setDurationDays(detectedDays ?? null);
    if (autoSplit && detectedDays && places.every((place) => place.day == null)) {
      places = await distributePlacesAcrossDays(places, detectedDays);
    }
    setResults(places);
    return response.data;
  };

  const waitForInitialImport = async (sid) => {
    const deadline = Date.now() + 60_000;
    while (Date.now() < deadline) {
      const response = await api.get(`/planning-sessions/${sid}`);
      if (response.data.status === 'DRAFT_READY') {
        await loadDraftPlaces(sid, true, true);
        return;
      }
      if (response.data.status === 'FAILED') {
        throw new Error(response.data.failureReason || 'We could not import these travel notes.');
      }
      await new Promise((resolve) => window.setTimeout(resolve, 2_000));
    }
    throw new Error('Import timed out. Please try again.');
  };

  const handleStartParsing = async () => {
    if (!text.trim() || isParsing) return;
    setIsParsing(true);
    setIsImporting(true);
    setImportError("");
    try {
      const response = await api.post('/planning-sessions', {
        title: `Plan for ${text.substring(0, 15)}...`,
        initialBrief: text
      });
      setSessionId(response.data.id);
      if (response.data.status === 'PROCESSING') {
        await waitForInitialImport(response.data.id);
      } else {
        await loadDraftPlaces(response.data.id, true, true);
      }
      setIsFinished(true);
    } catch (error) {
      console.error("Failed to start session:", error);
      setImportError(error.response?.data?.message || error.message || "AI Analysis failed. Please try again.");
    } finally {
      setIsParsing(false);
      setIsImporting(false);
    }
  };

  const updateItemName = async (id, newName) => {
    setResults(prev => prev.map(item => item.id === id ? { ...item, name: newName } : item));
    try {
      await api.put(`/planning-sessions/draft-places/${id}`, { name: newName });
    } catch (e) {
      console.error("Sync failed");
    }
  };

  const deleteItem = async (id) => {
    setResults(prev => prev.filter(item => item.id !== id));
    try {
      await api.delete(`/planning-sessions/draft-places/${id}`);
    } catch (e) {
      console.error("Delete failed");
    }
  };

  const handleRefine = async () => {
    if (!refineText.trim() || !sessionId) return;
    setIsParsing(true);
    try {
      await api.post(`/planning-sessions/${sessionId}/messages`, {
        role: 'user',
        content: refineText
      });
      await api.post(`/planning-sessions/${sessionId}/refine`);
      await loadDraftPlaces(sessionId, true, false);
      setRefineText("");
    } catch (e) {
      console.error(e);
      alert(e.response?.data?.message || "Refinement failed");
    } finally {
      setIsParsing(false);
    }
  };

  const handleConfirmImport = async () => {
    if (!sessionId) return;
    try {
      setIsParsing(true);

      if (targetTripId) {
        await addLocationsToTripDay(targetTripId, targetDay || 1, results.map(r => r.name));
        await fetchTrips();
        navigate(`/itinerary/${targetTripId}`);
        return;
      }

      if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 30) {
        setImportError('Tell us how many days this trip lasts (1–30) before confirming.');
        return;
      }

      let placesForConfirmation = results;
      if (placesForConfirmation.some((place) => place.day == null)) {
        placesForConfirmation = await distributePlacesAcrossDays(placesForConfirmation, durationDays);
        setResults(placesForConfirmation);
      }

      const validated = await api.post(`/planning-sessions/${sessionId}/validate-places`);
      const validatedPlaces = mapDraftPlaces(validated.data.draftPlaces || []);
      setResults(validatedPlaces);
      const unresolved = validatedPlaces.filter((place) => place.status !== 'ok').map((place) => place.name);
      if (unresolved.length > 0) {
        alert(
          `Could not locate these places on the map, so confirmation is blocked:\n${unresolved.join(', ')}\n\n`
          + 'Edit the names to clearer Singapore landmarks and try again.'
        );
        return;
      }
      // POST /planning-sessions/{id}/confirm — backend creates a brand new Trip.
      // The AI /recommend call it makes server-side (F-18) also returns suggestedAdditions
      // (nearby/similar places not already in the trip) — it's ephemeral, not persisted by
      // the backend, so it's handed to ItineraryDetailPage via navigation state only.
      const response = await api.post(`/planning-sessions/${sessionId}/confirm`, { durationDays });

      const { id: newTripId, weatherSummary, suggestedAdditions } = response.data;

      await fetchTrips();

      // Pass the AI summary data to the next page to show a welcome message
      // and drive the "Recommended Nearby Places" sidebar (F-18).
      navigate(`/itinerary/${newTripId}`, {
        state: {
          showAiSummary: true,
          weatherSummary,
          suggestedAdditions
        }
      });
    } catch (error) {
      console.error(error);
      alert(error.response?.data?.message || "Confirmation failed. Please try again.");
    } finally {
      setIsParsing(false);
    }
  };

  const handleReset = () => {
    setSessionId(null);
    setResults([]);
    setIsFinished(false);
    setDurationDays(null);
    setImportError("");
    setText("");
  };

  return (
    <div className="import-page" style={{ minHeight: '80vh', paddingBottom: '100px' }}>
      <div className="import-container" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <header className="page-header" style={{ textAlign: 'center', marginBottom: '40px' }}>
          <h1 style={{ fontSize: '32px' }}>Smart AI Import</h1>
          <p style={{ color: 'var(--muted)' }}>Turn your travel notes into a structured itinerary instantly.</p>
        </header>

        {!isFinished && (
          <div className="paste-area" style={{ background: '#fff', border: '2px dashed var(--jade)', borderRadius: '24px', padding: '24px' }}>
            <textarea
              style={{ width: '100%', minHeight: '200px', border: 'none', fontSize: '17px', outline: 'none', resize: 'none' }}
              placeholder="Paste or enter your travel notes here (e.g., from Instagram, blogs...)"
              value={text}
              onChange={(e) => setText(e.target.value)}
              disabled={isParsing}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px' }}>
              <button className="btn-primary" onClick={handleStartParsing} disabled={isParsing || !text.trim()}>
                {isImporting ? '🚀 Importing itinerary...' : isParsing ? '🚀 Analyzing...' : '✨ Start Parsing'}
              </button>
            </div>
            {importError && <p style={{ color: 'var(--coral)', margin: '12px 0 0' }}>{importError}</p>}
          </div>
        )}

        {isFinished && (
          <div className="parsing-results" style={{ marginTop: '20px' }}>
            <div className="agent-box" style={{ background: 'var(--mint)', padding: '20px', borderRadius: '16px', marginBottom: '24px', display: 'flex', gap: '16px' }}>
              <div style={{ fontSize: '24px' }}>🤖</div>
              <div>
                <strong>LoomyTrip AI Agent</strong>
                <p>
                  I&apos;ve extracted {results.length} locations
                  {durationDays ? ` and detected a ${durationDays}-day trip.` : '.'}
                  {' '}Review the automatic day split before confirming.
                </p>
              </div>
            </div>

            <div style={{ marginBottom: '24px', padding: '16px', background: '#fff', border: '1px solid var(--line-soft)', borderRadius: '16px' }}>
              <label style={{ display: 'block', fontWeight: 'bold', marginBottom: '8px' }} htmlFor="duration-days">
                Trip duration
              </label>
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                <input
                  id="duration-days"
                  type="number"
                  min="1"
                  max="30"
                  value={durationDays ?? ''}
                  onChange={(event) => setDurationDays(event.target.value === '' ? null : Number(event.target.value))}
                  style={{ width: '90px', padding: '10px', borderRadius: '8px', border: '1px solid var(--line)' }}
                />
                <span style={{ color: 'var(--muted)' }}>days</span>
                <button
                  className="btn-secondary"
                  onClick={async () => {
                    if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 30) {
                      setImportError('Enter a whole number from 1 to 30 first.');
                      return;
                    }
                    setIsParsing(true);
                    try {
                      setResults(await distributePlacesAcrossDays(results, durationDays));
                      setImportError('');
                    } catch (error) {
                      console.error(error);
                      setImportError('Could not save the automatic day split. Please try again.');
                    } finally {
                      setIsParsing(false);
                    }
                  }}
                  disabled={isParsing || results.length === 0}
                >
                  Auto-distribute places
                </button>
              </div>
              <p style={{ color: 'var(--muted)', margin: '10px 0 0', fontSize: '13px' }}>
                Places are split in the order extracted from your notes. You can change a place&apos;s day below.
              </p>
            </div>

            <div className="extracted-list" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {results.map((res) => (
                <div key={res.id} style={{ background: '#fff', border: '1px solid var(--line-soft)', padding: '16px', borderRadius: '16px', display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div style={{ fontSize: '20px' }}>{res.status === 'ok' ? '📍' : '⚠️'}</div>
                  <input
                    type="text"
                    value={res.name}
                    onChange={(e) => updateItemName(res.id, e.target.value)}
                    style={{ flex: 1, border: 'none', background: 'transparent', fontSize: '16px', fontWeight: 'bold' }}
                  />
                  {res.label !== 'Located' && (
                    <span style={{ fontSize: '12px', color: 'var(--muted)' }}>{res.label}</span>
                  )}
                  <select
                    aria-label={`Day for ${res.name}`}
                    value={res.day ?? 1}
                    onChange={async (event) => {
                      const day = Number(event.target.value);
                      setIsParsing(true);
                      try {
                        await persistPlaceDay(res, day);
                        setResults((current) => current.map((place) => place.id === res.id ? { ...place, day } : place));
                      } catch (error) {
                        console.error(error);
                        setImportError(`Could not save the day for ${res.name}.`);
                      } finally {
                        setIsParsing(false);
                      }
                    }}
                    disabled={isParsing || !durationDays}
                    style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--line)' }}
                  >
                    {Array.from({ length: durationDays || 1 }, (_, index) => index + 1).map((day) => (
                      <option key={day} value={day}>Day {day}</option>
                    ))}
                  </select>
                  <button onClick={() => deleteItem(res.id)} style={{ border: 'none', background: 'none', color: 'var(--coral)', cursor: 'pointer' }}>Delete</button>
                </div>
              ))}
            </div>
            {importError && <p style={{ color: 'var(--coral)', marginTop: '16px' }}>{importError}</p>}

            <div className="refine-area" style={{ marginTop: '32px', padding: '20px', background: 'var(--paper)', borderRadius: '16px' }}>
              <label style={{ display: 'block', marginBottom: '10px', fontWeight: 'bold' }}>Need adjustments?</label>
              <div style={{ display: 'flex', gap: '8px' }}>
                <input
                  type="text"
                  placeholder="e.g., 'Add a coffee shop near Marina Bay'..."
                  value={refineText}
                  onChange={(e) => setRefineText(e.target.value)}
                  style={{ flex: 1, padding: '12px', borderRadius: '10px', border: '1px solid var(--line)' }}
                />
                <button className="btn-secondary" onClick={handleRefine} disabled={isParsing || !refineText.trim()}>Refine</button>
              </div>
            </div>

            <div className="confirm-actions" style={{ marginTop: '48px', display: 'flex', justifyContent: 'center', gap: '16px' }}>
              <button className="btn-secondary" onClick={handleReset}>Restart</button>
              <button className="btn-primary" style={{ padding: '14px 40px' }} onClick={handleConfirmImport} disabled={isParsing || results.length === 0}>
                {isParsing ? 'Saving...' : 'Confirm & Generate Itinerary ➔'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportPage;
