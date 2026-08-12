import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

const mapDraftPlaces = (draftPlaces) =>
  draftPlaces.map((p) => ({
    id: p.id,
    name: p.name,
    selected: true,
    status: p.validationStatus === 'VALID' ? 'ok' : 'warn',
    label: p.validationStatus === 'VALID' ? 'Located' : 'Check Location',
  }));

const ImportPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { fetchTrips, addLocationsToTripDay } = useTrip();
  const { user } = useAuth();

  const targetTripId = searchParams.get('tripId');
  const targetDay = searchParams.get('day');

  const [sessionId, setSessionId] = useState(null);
  const [text, setText] = useState("");
  const [results, setResults] = useState([]);
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(false);
  const [refineText, setRefineText] = useState("");

  const loadDraftPlaces = async (sid, validate = true) => {
    if (validate) {
      await api.post(`/planning-sessions/${sid}/validate-places`);
    }
    const response = await api.get(`/planning-sessions/${sid}`);
    if (response.data.draftPlaces) {
      setResults(mapDraftPlaces(response.data.draftPlaces));
    } else {
      setResults([]);
    }
  };

  const handleStartParsing = async () => {
    if (!text.trim() || isParsing) return;
    setIsParsing(true);
    try {
      const response = await api.post('/planning-sessions', {
        title: `Plan for ${text.substring(0, 15)}...`,
        initialBrief: text
      });
      setSessionId(response.data.id);
      await loadDraftPlaces(response.data.id);
      setIsFinished(true);
    } catch (error) {
      console.error("Failed to start session:", error);
      alert("AI Analysis failed. Please try again.");
    } finally {
      setIsParsing(false);
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
      await loadDraftPlaces(sessionId);
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

      await api.post(`/planning-sessions/${sessionId}/validate-places`);
      const response = await api.post(`/planning-sessions/${sessionId}/confirm`);

      const { id: newTripId, weatherSummary, suggestedAdditions } = response.data;

      await fetchTrips();

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
                {isParsing ? '🚀 Analyzing...' : '✨ Start Parsing'}
              </button>
            </div>
          </div>
        )}

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
                  <span style={{ fontSize: '12px', color: 'var(--muted)' }}>{res.label}</span>
                  <button onClick={() => deleteItem(res.id)} style={{ border: 'none', background: 'none', color: 'var(--coral)', cursor: 'pointer' }}>Delete</button>
                </div>
              ))}
            </div>

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
