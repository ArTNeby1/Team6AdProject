import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { useAuth } from '../context/AuthContext';

const ImportPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { trips, createNewTrip, addLocationsToTripDay } = useTrip();
  const { user } = useAuth();

  const targetTripId = searchParams.get('tripId');
  const targetDay = searchParams.get('day');

  const targetTrip = trips.find(t => t.id === targetTripId);

  // Load initial state from LocalStorage
  const getInitialState = (key, defaultValue) => {
    const saved = localStorage.getItem(key);
    try {
      return saved ? JSON.parse(saved) : defaultValue;
    } catch {
      return defaultValue;
    }
  };

  const [text, setText] = useState(getInitialState('import_text', "Please paste or enter your travel notes here"));
  const [results, setResults] = useState(getInitialState('import_results', []));
  const [isParsing, setIsParsing] = useState(false);
  const [isFinished, setIsFinished] = useState(getInitialState('import_is_finished', false));

  const intervalRef = useRef(null);

  // Persistence
  useEffect(() => {
    localStorage.setItem('import_text', JSON.stringify(text));
    localStorage.setItem('import_results', JSON.stringify(results));
    localStorage.setItem('import_is_finished', JSON.stringify(isFinished));
  }, [text, results, isFinished]);

  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  const handleStartParsing = (e) => {
    if (e) e.preventDefault();
    if (isParsing || !text.trim()) return;

    setResults([]);
    setIsFinished(false);
    setIsParsing(true);

    const mockAIItems = [
      { id: 'ext-1', name: 'Wat Chedi Luang', status: 'ok', label: 'Located', selected: true },
      { id: 'ext-2', name: 'Tha Phae Gate', status: 'ok', label: 'Located', selected: true },
      { id: 'ext-3', name: 'Nimman Road', status: 'ok', label: 'Located', selected: true },
      { id: 'ext-4', name: 'Sunday Night Market', status: 'warn', label: 'Confirm Time', selected: true },
    ];

    let currentIndex = 0;
    intervalRef.current = setInterval(() => {
      if (currentIndex < mockAIItems.length) {
        const itemToAdd = mockAIItems[currentIndex];
        setResults(prev => {
          if (prev.find(p => p.id === itemToAdd.id)) return prev;
          return [...prev, itemToAdd];
        });
        currentIndex++;
      } else {
        clearInterval(intervalRef.current);
        setIsParsing(false);
        setIsFinished(true);
      }
    }, 800);
  };

  const toggleItemSelection = (id) => {
    setResults(prev => prev.map(item =>
      item.id === id ? { ...item, selected: !item.selected } : item
    ));
  };

  const deleteItem = (id) => {
    setResults(prev => prev.filter(item => item.id !== id));
  };

  const updateItemName = (id, newName) => {
    setResults(prev => prev.map(item =>
      item.id === id ? { ...item, name: newName } : item
    ));
  };

  const handleConfirmImport = (e) => {
    if (e) e.preventDefault();

    const selectedItems = results.filter(r => r.selected);
    if (selectedItems.length === 0) {
      alert("Please select at least one location to add to the itinerary");
      return;
    }

    try {
      if (targetTripId && targetDay) {
        addLocationsToTripDay(targetTripId, targetDay, selectedItems.map(item => item.name));
        handleReset();
        navigate(`/itinerary/${targetTripId}`);
      } else {
        // Use global user preferences from AuthContext
        const preferences = {
          travelStyle: user?.travelStyle || 'Cultural',
          preferTransport: user?.preferTransport || 'Public'
        };
        const newTripId = createNewTrip(selectedItems.map(item => item.name), preferences);
        handleReset();
        navigate(`/itinerary/${newTripId}`);
      }
    } catch (error) {
      console.error("Import failed:", error);
      alert("Failed to add trip, please refresh and try again");
    }
  };

  const handleReset = () => {
    setResults([]);
    setIsParsing(false);
    setIsFinished(false);
    localStorage.removeItem('import_results');
    localStorage.removeItem('import_is_finished');
  };

  const getStyleLabel = (style) => {
    const labels = {
      'Cultural': 'Cultural Depth',
      'Leisure': 'Leisure Vacation',
      'Adventure': 'Outdoor Adventure',
      'Foodie': 'Gourmet Tasting'
    };
    return labels[style] || 'Personalized';
  };

  return (
    <div className="import-page" style={{ minHeight: '80vh', paddingBottom: '100px' }}>
      <div className="import-container" style={{ maxWidth: '800px', margin: '0 auto' }}>
        <header className="page-header" style={{ textAlign: 'center', marginBottom: '40px' }}>
          <h1 style={{ fontSize: '32px', marginBottom: '12px' }}>
            {targetTrip ? `Import to Itinerary: ${targetTrip.title}` : 'Import Trip'}
          </h1>
          <p style={{ color: 'var(--muted)', fontSize: '16px' }}>
            {targetTrip
              ? `AI will add the parsed attractions directly to Day ${targetDay}`
              : 'LoomyTrip AI will extract attractions, locate them, and connect them into an optimal route for you'}
          </p>
        </header>

        <div className="paste-area" style={{
          background: '#fff', border: '2px dashed var(--jade)',
          borderRadius: '24px', padding: '24px', boxShadow: 'var(--shadow-sm)'
        }}>
          <textarea
            style={{
              width: '100%', minHeight: '160px', border: 'none',
              fontSize: '17px', lineHeight: '1.7', outline: 'none',
              resize: 'none', color: 'var(--ink)'
            }}
            placeholder="Paste travel notes or text content here..."
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
              {isParsing ? '🚀 Extracting attractions intelligently...' : '✨ Start Parsing'}
            </button>
          </div>
        </div>

        {(results.length > 0 || isParsing) && (
          <div className="parsing-status" style={{ marginTop: '48px' }}>
            <div className="agent-box" style={{
              display: 'flex', alignItems: 'center', gap: '16px',
              background: 'var(--mint)', padding: '20px 24px', borderRadius: '16px',
              marginBottom: '32px', border: '1px solid var(--line-soft)'
            }}>
              <div style={{ width: '44px', height: '44px', background: 'var(--amber)', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px' }}>🤖</div>
              <div className="agent-text">
                <strong style={{ color: 'var(--jade-deep)', fontSize: '18px' }}>LoomyTrip AI Agent</strong>
                <p style={{ marginTop: '4px', color: 'var(--ink-70)' }}>
                   Parsing complete! Based on the <b>{getStyleLabel(user?.travelStyle)}</b> style you set in the Profile, the following attractions are recommended for you.
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
                  boxShadow: res.selected ? 'var(--shadow-sm)' : 'none'
                }}>
                  <div
                    onClick={() => toggleItemSelection(res.id)}
                    style={{
                      width: '28px', height: '28px', border: `2px solid ${res.selected ? 'var(--jade)' : 'var(--line)'}`,
                      borderRadius: '8px', cursor: 'pointer', display: 'flex',
                      alignItems: 'center', justifyContent: 'center', color: 'var(--jade)',
                      background: res.selected ? 'var(--mint)' : 'transparent',
                      fontSize: '18px', fontWeight: 'bold'
                    }}
                  >
                    {res.selected ? '✓' : ''}
                  </div>
                  <div style={{ fontSize: '24px' }}>{res.status === 'ok' ? '📍' : '⚠️'}</div>
                  <input
                    type="text"
                    value={res.name}
                    onChange={(e) => updateItemName(res.id, e.target.value)}
                    style={{
                      flex: 1, border: '1px solid transparent', background: 'transparent',
                      fontSize: '18px', fontWeight: '700', padding: '6px 10px',
                      borderRadius: '8px', color: 'var(--ink)', width: '100%'
                    }}
                  />
                  <span style={{
                    padding: '6px 16px', borderRadius: '99px', fontSize: '13px', fontWeight: '800',
                    background: res.status === 'ok' ? 'var(--mint)' : '#FCEFD6',
                    color: res.status === 'ok' ? 'var(--jade-deep)' : '#9a6410',
                    whiteSpace: 'nowrap'
                  }}>{res.label}</span>
                  <button
                    type="button"
                    onClick={() => deleteItem(res.id)}
                    style={{
                      border: 'none', background: 'var(--line-soft)', color: 'var(--muted)',
                      width: '28px', height: '28px', borderRadius: '50%', cursor: 'pointer',
                      fontSize: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}
                  >×</button>
                </div>
              ))}
            </div>

            {!isParsing && results.length > 0 && (
              <div className="confirm-actions" style={{ marginTop: '56px', display: 'flex', justifyContent: 'center', gap: '20px' }}>
                <button type="button" className="btn-secondary" onClick={handleReset} style={{ padding: '14px 32px', borderRadius: '99px' }}>
                  Re-parse
                </button>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={handleConfirmImport}
                  style={{
                    padding: '14px 60px', borderRadius: '99px', fontSize: '18px',
                    boxShadow: '0 8px 20px rgba(14, 158, 142, 0.3)'
                  }}
                >
                  Confirm and add {results.filter(r => r.selected).length} locations to the itinerary ➔
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
