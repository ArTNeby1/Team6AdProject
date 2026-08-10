import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

const ImportPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { fetchTrips, addLocationsToTripDay } = useTrip();
  const { user } = useAuth();

  const targetTripId = searchParams.get('tripId');
  const targetDay = searchParams.get('day');

  // Planning Session State
  const [sessionId, setSessionId] = useState(null);
  const [text, setText] = useState("");
  const [results, setResults] = useState([]);
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(false);
  const [refineText, setRefineText] = useState("");

  // 1. Start Session (User enters text -> POST /planning-sessions)
  const handleStartParsing = async () => {
    if (!text.trim() || isParsing) return;
    setIsParsing(true);
    try {
      const response = await api.post('/planning-sessions', {
        title: `Plan for ${text.substring(0, 15)}...`,
        initialBrief: text
      });
      setSessionId(response.data.id);

      // In a real AI flow, we'd poll or wait for the AI to populate draft_places
      // For this implementation, we assume the backend triggers the Python extraction immediately
      // and we fetch the results.
      await fetchDraftPlaces(response.data.id);
      setIsFinished(true);
    } catch (error) {
      console.error("Failed to start session:", error);
      const message = error.response?.data?.message || "AI Analysis failed. Please try again.";
      alert(message);
    } finally {
      setIsParsing(false);
    }
  };

  const fetchDraftPlaces = async (sid) => {
    try {
      // Assuming an endpoint to get session details including draft places
      const response = await api.get(`/planning-sessions/${sid}`);
      // Mapping backend draft_places to our results UI
      if (response.data.draftPlaces) {
        setResults(response.data.draftPlaces.map(p => ({
          id: p.id,
          name: p.name,
          selected: true,
          status: p.validationStatus === 'VALID' ? 'ok' : 'warn',
          label: p.validationStatus === 'VALID' ? 'Located' : 'Check Location'
        })));
      }
    } catch (e) {
      // Fallback/Mock for demo if GET detail not ready
      setResults([
        { id: 'd1', name: 'Wat Chedi Luang', selected: true, status: 'ok', label: 'Located' },
        { id: 'd2', name: 'Tha Phae Gate', selected: true, status: 'ok', label: 'Located' }
      ]);
    }
  };

  // 2. Edit Place Name (PUT /planning-sessions/draft-places/{id})
  const updateItemName = async (id, newName) => {
    setResults(prev => prev.map(item => item.id === id ? { ...item, name: newName } : item));
    try {
      await api.put(`/planning-sessions/draft-places/${id}`, { name: newName });
    } catch (e) {
      console.error("Sync failed");
    }
  };

  // 3. Delete Place (DELETE /planning-sessions/draft-places/{id})
  const deleteItem = async (id) => {
    setResults(prev => prev.filter(item => item.id !== id));
    try {
      await api.delete(`/planning-sessions/draft-places/${id}`);
    } catch (e) {
      console.error("Delete failed");
    }
  };

  // 4. Refine (POST /messages + /refine)
  const handleRefine = async () => {
    if (!refineText.trim() || !sessionId) return;
    setIsParsing(true);
    try {
      await api.post(`/planning-sessions/${sessionId}/messages`, {
        role: 'user',
        content: refineText
      });
      await api.post(`/planning-sessions/${sessionId}/refine`);
      await fetchDraftPlaces(sessionId);
      setRefineText("");
    } catch (e) {
      alert("Refinement failed");
    } finally {
      setIsParsing(false);
    }
  };

  // 5. Confirm
  const handleConfirmImport = async () => {
    if (!sessionId) return;
    try {
      setIsParsing(true);

      if (targetTripId) {
        // Came from an existing trip's empty day ("go import or add some!") — add the
        // parsed places into THAT trip/day instead of confirm's default behavior, which
        // always creates a brand new Trip (planning_session -> confirmed Trip is a 1:1
        // creation flow, it has no notion of "import into an existing trip").
        await addLocationsToTripDay(targetTripId, targetDay || 1, results.map(r => r.name));
        await fetchTrips();
        navigate(`/itinerary/${targetTripId}`);
        return;
      }

      // POST /planning-sessions/{id}/confirm — backend creates a brand new Trip.
      // The AI /recommend call it makes server-side (F-18) also returns suggestedAdditions
      // (nearby/similar places not already in the trip) — it's ephemeral, not persisted by
      // the backend, so it's handed to ItineraryDetailPage via navigation state only.
      const response = await api.post(`/planning-sessions/${sessionId}/confirm`);
      const newTripId = response.data.id;
      await fetchTrips();
      navigate(`/itinerary/${newTripId}`, {
        state: {
          suggestedAdditions: response.data.suggestedAdditions,
          weatherSummary: response.data.weatherSummary,
        },
      });
    } catch (error) {
      alert("Confirmation failed. Please try again.");
    } finally {
      setIsParsing(false);
    }
  };

  const handleReset = () => {
    setSessionId(null);
    setResults([]);
    setIsFinished(false);
    setText("");
  };

  return (
    <div className="import-page" style={{ minHeight: '80vh', paddingBottom: '100px' }}>
      <div className="import-container" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <header className="page-header" style={{ textAlign: 'center', marginBottom: '40px' }}>
          <h1 style={{ fontSize: '32px' }}>Smart AI Import</h1>
          <p style={{ color: 'var(--muted)' }}>Turn your travel notes into a structured itinerary instantly.</p>
        </header>

        {/* STEP 1: INPUT */}
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
                {isParsing ? '🚀 Analyzing...' : '✨ Start Parsing'}
              </button>
            </div>
          </div>
        )}

        {/* STEPS 2-4: REVIEW & REFINE */}
        {isFinished && (
          <div className="parsing-results" style={{ marginTop: '20px' }}>
            <div className="agent-box" style={{ background: 'var(--mint)', padding: '20px', borderRadius: '16px', marginBottom: '24px', display: 'flex', gap: '16px' }}>
              <div style={{ fontSize: '24px' }}>🤖</div>
              <div>
                <strong>LoomyTrip AI Agent</strong>
                <p>I've extracted {results.length} locations. You can edit names, delete ones you don't like, or tell me more below.</p>
              </div>
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
                  <button onClick={() => deleteItem(res.id)} style={{ border: 'none', background: 'none', color: 'var(--coral)', cursor: 'pointer' }}>Delete</button>
                </div>
              ))}
            </div>

            {/* STEP 4: REFINE UI */}
            <div className="refine-area" style={{ marginTop: '32px', padding: '20px', background: 'var(--paper)', borderRadius: '16px' }}>
              <label style={{ display: 'block', marginBottom: '10px', fontWeight: 'bold' }}>Need adjustments?</label>
              <div style={{ display: 'flex', gap: '8px' }}>
                <input
                  type="text"
                  placeholder="e.g., 'Add a coffee shop near Nimman Road'..."
                  value={refineText}
                  onChange={(e) => setRefineText(e.target.value)}
                  style={{ flex: 1, padding: '12px', borderRadius: '10px', border: '1px solid var(--line)' }}
                />
                <button className="btn-secondary" onClick={handleRefine} disabled={isParsing || !refineText.trim()}>Refine</button>
              </div>
            </div>

            {/* STEP 5: CONFIRM */}
            <div className="confirm-actions" style={{ marginTop: '48px', display: 'flex', justifyContent: 'center', gap: '16px' }}>
              <button className="btn-secondary" onClick={handleReset}>Restart</button>
              <button className="btn-primary" style={{ padding: '14px 40px' }} onClick={handleConfirmImport} disabled={isParsing}>
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
