import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';
import { apiFetch } from '../api';

/** Simple client-side place candidates until AI refine returns draft_place rows. */
function extractPlaceCandidates(text) {
  return [...new Set(
    text
      .split(/[\n,;]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 2 && s.length < 80)
  )]
    .slice(0, 20)
    .map((name, i) => ({
      id: `local-${i}-${name}`,
      name,
      status: 'ok',
      label: 'From notes',
      selected: true,
    }));
}

const ImportPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { trips, createNewTrip, addLocationsToTripDay } = useTrip();
  const { user } = useAuth();

  const targetTripId = searchParams.get('tripId');
  const targetDay = searchParams.get('day');
  const targetTrip = trips.find((t) => String(t.id) === String(targetTripId));

  const getInitialState = (key, defaultValue) => {
    const saved = localStorage.getItem(key);
    try {
      return saved ? JSON.parse(saved) : defaultValue;
    } catch {
      return defaultValue;
    }
  };

  const [text, setText] = useState(getInitialState('import_text', ''));
  const [results, setResults] = useState(getInitialState('import_results', []));
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(getInitialState('import_is_finished', false));
  const [sessionId, setSessionId] = useState(getInitialState('import_session_id', null));
  const [error, setError] = useState('');
  const [isConfirming, setIsConfirming] = useState(false);

  const intervalRef = useRef(null);

  useEffect(() => {
    localStorage.setItem('import_text', JSON.stringify(text));
    localStorage.setItem('import_results', JSON.stringify(results));
    localStorage.setItem('import_is_finished', JSON.stringify(isFinished));
    localStorage.setItem('import_session_id', JSON.stringify(sessionId));
  }, [text, results, isFinished, sessionId]);

  useEffect(() => () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
  }, []);

  const handleStartParsing = async (e) => {
    if (e) e.preventDefault();
    if (isParsing || !text.trim()) return;

    setError('');
    setResults([]);
    setIsFinished(false);
    setIsParsing(true);
    setSessionId(null);

    try {
      const session = await apiFetch('/api/v1/planning-sessions', {
        method: 'POST',
        body: {
          title: text.trim().slice(0, 60) || 'Planning session',
          initialBrief: text.trim(),
        },
      });
      setSessionId(session.id);

      const candidates = extractPlaceCandidates(text);
      setResults(candidates.length > 0
        ? candidates
        : [{
          id: `session-${session.id}`,
          name: `Planning session #${session.id}`,
          status: 'ok',
          label: 'Session saved',
          selected: true,
        }]);
      setIsFinished(true);
    } catch (err) {
      setError(err.message || 'Failed to create planning session');
    } finally {
      setIsParsing(false);
    }
  };

  const toggleItemSelection = (id) => {
    setResults((prev) => prev.map((item) =>
      item.id === id ? { ...item, selected: !item.selected } : item
    ));
  };

  const deleteItem = (id) => {
    setResults((prev) => prev.filter((item) => item.id !== id));
  };

  const updateItemName = (id, newName) => {
    setResults((prev) => prev.map((item) =>
      item.id === id ? { ...item, name: newName } : item
    ));
  };

  const handleConfirmImport = async (e) => {
    if (e) e.preventDefault();

    const selectedItems = results.filter((r) => r.selected);
    if (selectedItems.length === 0) {
      alert('Please select at least one location to add to the itinerary');
      return;
    }

    setIsConfirming(true);
    setError('');
    try {
      if (targetTripId && targetDay) {
        addLocationsToTripDay(targetTripId, targetDay, selectedItems.map((item) => item.name));
        handleReset();
        navigate(`/itinerary/${targetTripId}`);
      } else {
        const preferences = {
          travelStyle: user?.travelStyle || 'Cultural',
          preferTransport: user?.preferTransport || 'Public',
        };
        const newTripId = await createNewTrip(
          selectedItems.map((item) => item.name),
          preferences,
          { tripName: text.trim().slice(0, 80) || 'Imported trip' }
        );
        handleReset();
        navigate(`/itinerary/${newTripId}`);
      }
    } catch (err) {
      console.error('Import failed:', err);
      setError(err.message || 'Failed to create trip');
    } finally {
      setIsConfirming(false);
    }
  };

  const handleReset = () => {
    setResults([]);
    setIsParsing(false);
    setIsFinished(false);
    setSessionId(null);
    setError('');
    localStorage.removeItem('import_results');
    localStorage.removeItem('import_is_finished');
    localStorage.removeItem('import_session_id');
  };

  const getStyleLabel = (style) => {
    const labels = {
      Cultural: 'Cultural Depth',
      Leisure: 'Leisure Vacation',
      Adventure: 'Outdoor Adventure',
      Foodie: 'Gourmet Tasting',
    };
    return labels[style] || 'Personalized';
  };

  return (
    <div className="import-page" style={{ minHeight: '80vh', paddingBottom: '100px' }}>
      <div className="import-container" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <header className="page-header" style={{ textAlign: 'center', marginBottom: '40px' }}>
          <h1 style={{ fontSize: '32px', marginBottom: '12px' }}>
            {targetTrip ? `Import to Itinerary: ${targetTrip.title}` : 'Plan a Trip'}
          </h1>
          <p style={{ color: 'var(--muted)', fontSize: '16px' }}>
            {targetTrip
              ? `Your notes will be saved as a planning session, then stops can be added to Day ${targetDay}`
              : 'Your brief is saved to a planning session on the server. Place candidates are taken from your notes until AI refine is ready.'}
          </p>
        </header>

        <div className="paste-area" style={{
          background: '#fff', border: '2px dashed var(--jade)',
          borderRadius: '24px', padding: '24px', boxShadow: 'var(--shadow-sm)',
        }}>
          <textarea
            style={{
              width: '100%', minHeight: '160px', border: 'none',
              fontSize: '17px', lineHeight: '1.7', outline: 'none',
              resize: 'none', color: 'var(--ink)',
            }}
            placeholder="Paste travel notes or a rough brief here (one place per line works best)..."
            value={text}
            onChange={(e) => setText(e.target.value)}
            disabled={isParsing}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px' }}>
            <button
              type="button"
              className="btn-primary"
              onClick={handleStartParsing}
              disabled={isParsing || !text.trim()}
              style={{ padding: '12px 40px', fontSize: '16px' }}
            >
              {isParsing ? 'Saving planning session...' : 'Start Planning'}
            </button>
          </div>
        </div>

        {error && (
          <p style={{ color: '#b42318', marginTop: '16px', textAlign: 'center' }}>{error}</p>
        )}

        {sessionId && (
          <p style={{ color: 'var(--jade-deep)', marginTop: '16px', textAlign: 'center', fontWeight: 600 }}>
            Planning session #{sessionId} saved on the server
          </p>
        )}

        {(results.length > 0 || isParsing) && (
          <div className="parsing-status" style={{ marginTop: '48px' }}>
            <div className="agent-box" style={{
              display: 'flex', alignItems: 'center', gap: '16px',
              background: 'var(--mint)', padding: '20px 24px', borderRadius: '16px',
              marginBottom: '32px', border: '1px solid var(--line-soft)',
            }}>
              <div style={{
                width: '44px', height: '44px', background: 'var(--amber)', borderRadius: '12px',
                display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px',
              }}>
                ✓
              </div>
              <div className="agent-text">
                <strong style={{ color: 'var(--jade-deep)', fontSize: '18px' }}>Planning session ready</strong>
                <p style={{ marginTop: '4px', color: 'var(--ink-70)' }}>
                  Brief stored for later AI refine. Review place candidates
                  {user?.travelStyle ? <> using your <b>{getStyleLabel(user.travelStyle)}</b> preference</> : null}.
                </p>
              </div>
            </div>

            <div className="extracted-list" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {results.map((res) => (
                <div key={res.id} style={{
                  background: '#fff', border: '1px solid var(--line-soft)',
                  padding: '16px 24px', borderRadius: '20px', display: 'flex',
                  alignItems: 'center', gap: '20px', transition: 'all 0.3s',
                  opacity: res.selected ? 1 : 0.5,
                  boxShadow: res.selected ? 'var(--shadow-sm)' : 'none',
                }}>
                  <div
                    onClick={() => toggleItemSelection(res.id)}
                    style={{
                      width: '28px', height: '28px', border: `2px solid ${res.selected ? 'var(--jade)' : 'var(--line)'}`,
                      borderRadius: '8px', cursor: 'pointer', display: 'flex',
                      alignItems: 'center', justifyContent: 'center', color: 'var(--jade)',
                      background: res.selected ? 'var(--mint)' : 'transparent',
                      fontSize: '18px', fontWeight: 'bold',
                    }}
                  >
                    {res.selected ? '✓' : ''}
                  </div>
                  <input
                    type="text"
                    value={res.name}
                    onChange={(e) => updateItemName(res.id, e.target.value)}
                    style={{
                      flex: 1, border: '1px solid transparent', background: 'transparent',
                      fontSize: '18px', fontWeight: '700', padding: '6px 10px',
                      borderRadius: '8px', color: 'var(--ink)', width: '100%',
                    }}
                  />
                  <span style={{
                    padding: '6px 16px', borderRadius: '99px', fontSize: '13px', fontWeight: '800',
                    background: 'var(--mint)', color: 'var(--jade-deep)', whiteSpace: 'nowrap',
                  }}>
                    {res.label}
                  </span>
                  <button
                    type="button"
                    onClick={() => deleteItem(res.id)}
                    style={{
                      border: 'none', background: 'var(--line-soft)', color: 'var(--muted)',
                      width: '28px', height: '28px', borderRadius: '50%', cursor: 'pointer',
                      fontSize: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>

            {!isParsing && isFinished && results.length > 0 && (
              <div className="confirm-actions" style={{ marginTop: '56px', display: 'flex', justifyContent: 'center', gap: '20px' }}>
                <button type="button" className="btn-secondary" onClick={handleReset} style={{ padding: '14px 32px', borderRadius: '99px' }}>
                  Start over
                </button>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={handleConfirmImport}
                  disabled={isConfirming}
                  style={{
                    padding: '14px 60px', borderRadius: '99px', fontSize: '18px',
                    boxShadow: '0 8px 20px rgba(14, 158, 142, 0.3)',
                  }}
                >
                  {isConfirming
                    ? 'Creating trip...'
                    : `Confirm and add ${results.filter((r) => r.selected).length} locations ➔`}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default ImportPage;
