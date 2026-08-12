import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

const EditPage = () => {
  const navigate = useNavigate();
  const { getActiveTrip, saveTripEdits } = useTrip();
  const trip = getActiveTrip();

  // Local state for "Draft" mode
  const [localLocations, setLocalLocations] = useState([]);
  const [localDayCount, setLocalDayCount] = useState(1);
  const [manualAddDay, setManualAddDay] = useState(null);
  const [manualName, setManualAddName] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // Initial Sort helper
  const sortByTime = (items) => {
    return [...items].sort((a, b) => {
      if (a.day !== b.day) return a.day - b.day;
      return (a.time || '').localeCompare(b.time || '');
    });
  };

  // Initialize local state from context
  useEffect(() => {
    if (trip) {
      setLocalLocations(sortByTime([...trip.locations]));
      setLocalDayCount(trip.dayCount || 1);
    }
  }, [trip]);

  if (!trip) return <div>Trip not found</div>;

  if (trip.status === 'FINISHED') {
    return (
      <div className="container" style={{textAlign: 'center', padding: '100px 0'}}>
        <h2>This itinerary has ended and cannot be edited.</h2>
        <button className="btn-primary" style={{marginTop: '20px'}} onClick={() => navigate(`/itinerary/${trip.id}`)}>Back to Details</button>
      </div>
    );
  }

  const onDragEnd = (result) => {
    const { source, destination, draggableId } = result;
    if (!destination) return;
    if (source.droppableId === destination.droppableId && source.index === destination.index) return;

    const sourceDay = parseInt(source.droppableId.split('-')[1]);
    const destDay = parseInt(destination.droppableId.split('-')[1]);

    const newFullList = Array.from(localLocations);
    const sourceIdx = newFullList.findIndex(l => l.id === draggableId);
    const [movedItem] = newFullList.splice(sourceIdx, 1);

    // Requirement 2: Swap times based on slots
    // If moving within the same day or to another day, the item takes the time of the target slot.
    // The items being shifted will move to new slots and take their times.

    // To implement "times are properties of slots":
    // 1. Group items by day
    // 2. For the destination day, get the sequence of times currently present
    // 3. Insert the item, re-distribute the times

    const dayGroups = {};
    for (let d = 1; d <= localDayCount; d++) {
        dayGroups[d] = newFullList.filter(l => l.day === d);
    }

    // Insert into target day helper
    movedItem.day = destDay;
    dayGroups[destDay].splice(destination.index, 0, movedItem);

    // Re-assign times for the affected day(s)
    // We assume the times are sorted in the UI.
    // If an item moves to a position, it should ideally represent that time slot.
    // However, since times are editable, we'll keep the list of times per day constant but change who owns them.

    const updateDayTimes = (day) => {
        const originalDayItems = localLocations.filter(l => l.day === day);
        const originalTimes = originalDayItems.map(l => l.time).sort();

        dayGroups[day].forEach((item, idx) => {
            if (originalTimes[idx]) {
                item.time = originalTimes[idx];
            } else {
                // If we moved an item TO this day and it has more items than before
                // We'll give it a sensible default if the original times are exhausted
                if (!item.time || item.time === '待定') item.time = '09:00';
            }
        });
    };

    updateDayTimes(destDay);
    if (sourceDay !== destDay) {
        updateDayTimes(sourceDay);
    }

    // Flatten back
    const finalResult = [];
    for (let d = 1; d <= localDayCount; d++) {
        finalResult.push(...dayGroups[d]);
    }

    setLocalLocations(finalResult);
  };

  const updateItemField = (id, field, value) => {
    setLocalLocations(prev => prev.map(item =>
      item.id === id ? { ...item, [field]: value } : item
    ));
  };

  // Requirement 1: Time Input Mask (HH:mm, max 23:59, fixed colon)
  const handleTimeChange = (id, val) => {
    // Remove non-numeric
    let clean = val.replace(/\D/g, '');
    if (clean.length > 4) clean = clean.substring(0, 4);

    let hh = clean.substring(0, 2);
    let mm = clean.substring(2, 4);

    // Validate HH
    if (hh && parseInt(hh) > 23) hh = '23';
    // Validate MM
    if (mm && parseInt(mm) > 59) mm = '59';

    let formatted = hh;
    if (clean.length >= 2) formatted += ':';
    formatted += mm;

    updateItemField(id, 'time', formatted);
  };

  const handleDelete = (id) => {
    setLocalLocations(localLocations.filter(loc => loc.id !== id));
  };

  const handleManualAdd = (day) => {
    if (!manualName.trim()) return;
    const newLoc = {
      id: `manual-${Date.now()}`,
      name: manualName.trim(),
      day: day,
      time: '09:00',
      activityType: 'Visit',
      duration: '1.5',
      transport: '🚕 TBD'
    };
    setLocalLocations([...localLocations, newLoc]);
    setManualAddName('');
    setManualAddDay(null);
  };

  const handleSave = async () => {
    const sortedLocations = sortByTime(localLocations);
    setIsSaving(true);
    try {
      // saveTripEdits rethrows on failure — must await it before navigating away, otherwise
      // this was firing the request and immediately leaving the page regardless of outcome,
      // silently discarding the edits whenever the reorder request failed.
      await saveTripEdits(trip.id, sortedLocations, localDayCount);
      navigate(`/itinerary/${trip.id}`);
    } catch (error) {
      alert(error.response?.data?.message || 'Failed to save changes, please try again');
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = () => {
    setLocalLocations(sortByTime([...trip.locations]));
    setLocalDayCount(trip.dayCount || 1);
  };

  const checkTimeConflict = (item) => {
    return localLocations.some(l =>
      l.day === item.day &&
      l.id !== item.id &&
      l.time === item.time &&
      l.time !== '待定'
    );
  };

  const daysArray = Array.from({ length: localDayCount }, (_, i) => i + 1);

  return (
    <div className="edit-page">
      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
        <div>
          <button className="btn-secondary" style={{padding: '6px 16px', marginBottom: '12px', fontSize: '14px'}} onClick={() => navigate(`/itinerary/${trip.id}`)}>
            ← Back to Details
          </button>
          <h1>Edit Itinerary</h1>
          <p>{trip.title} · Total {localDayCount} Days</p>
        </div>
        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          <button className="btn-secondary" onClick={handleReset}>Reset Changes</button>
          <button className="btn-primary" onClick={handleSave} disabled={isSaving}>{isSaving ? 'Saving...' : 'Save Changes'}</button>
        </div>
      </header>

      <div className="edit-container" style={{maxWidth: '900px', margin: '0 auto'}}>
        <div className="info-card" style={{background: 'var(--mint)', border: 'none', marginBottom: '32px'}}>
          <p style={{fontSize: '15px', color: 'var(--jade-deep)', fontWeight: 600}}>
            💡 Tip: Supports 24-hour format editing. Dragging attractions will automatically lock time slots, and conflicting times will show a red warning.
          </p>
        </div>

        <DragDropContext onDragEnd={onDragEnd}>
          {daysArray.map(day => {
            const dayLocations = localLocations.filter(loc => loc.day === day);
            return (
              <div key={day} className="edit-day-section" style={{marginBottom: '48px'}}>
                <h2 style={{fontSize: '20px', marginBottom: '16px', borderBottom: '2px solid var(--line-soft)', paddingBottom: '8px'}}>
                  Day {day}
                </h2>

                <Droppable droppableId={`day-${day}`}>
                  {(provided, snapshot) => (
                    <div
                      ref={provided.innerRef}
                      {...provided.droppableProps}
                      className="edit-list"
                      style={{
                        minHeight: '20px',
                        background: snapshot.isDraggingOver ? 'var(--mint)' : 'transparent',
                        borderRadius: '12px',
                        transition: 'background 0.2s'
                      }}
                    >
                      {dayLocations.map((item, idx) => {
                        const isConflict = checkTimeConflict(item);
                        return (
                          <Draggable key={item.id} draggableId={item.id} index={idx}>
                            {(provided, snapshot) => (
                              <div
                                ref={provided.innerRef}
                                {...provided.draggableProps}
                                className={`edit-card ${snapshot.isDragging ? 'dragging' : ''}`}
                                style={{
                                  ...provided.draggableProps.style,
                                  marginBottom: '12px',
                                  padding: '16px 24px'
                                }}
                              >
                                <div className="drag-handle" {...provided.dragHandleProps} style={{ marginRight: '10px' }}>⋮⋮</div>
                                <div className="edit-num" style={{ minWidth: '32px', height: '32px', fontSize: '16px', marginRight: '16px' }}>{idx + 1}</div>
                                <div className="edit-info">
                                  <h3 style={{ fontSize: '18px', marginBottom: '10px' }}>{item.name}</h3>
                                  <div className="edit-time-select" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                    {/* Requirement 1: Time Input Mask */}
                                    <div style={{ display: 'flex', alignItems: 'center', background: 'var(--paper)', borderRadius: '8px', padding: '2px 8px' }}>
                                      <span style={{ fontSize: '12px', color: 'var(--muted)', marginRight: '4px' }}>Start</span>
                                      <input
                                        type="text"
                                        value={item.time}
                                        onChange={(e) => handleTimeChange(item.id, e.target.value)}
                                        placeholder="00:00"
                                        style={{
                                          width: '60px', border: 'none', background: 'transparent',
                                          color: isConflict ? 'var(--coral)' : 'var(--ink)',
                                          fontWeight: '700', textAlign: 'center', outline: 'none'
                                        }}
                                      />
                                    </div>

                                    {/* Duration (In DB) */}
                                    <div style={{ display: 'flex', alignItems: 'center', background: 'var(--paper)', borderRadius: '8px', padding: '2px 8px' }}>
                                      <input
                                        type="text"
                                        value={item.duration}
                                        onChange={(e) => updateItemField(item.id, 'duration', e.target.value)}
                                        placeholder="1.5"
                                        style={{
                                          width: '40px', border: 'none', background: 'transparent',
                                          fontSize: '13px', fontWeight: '700', textAlign: 'center', outline: 'none'
                                        }}
                                      />
                                      <span style={{ fontSize: '12px', color: 'var(--muted)' }}>h</span>
                                    </div>
                                  </div>
                                </div>
                                <div className="edit-actions">
                                  <button className="btn-del" onClick={() => handleDelete(item.id)}>Delete</button>
                                </div>
                              </div>
                            )}
                          </Draggable>
                        );
                      })}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>

                {manualAddDay === day ? (
                  <div style={{ marginTop: '12px', display: 'flex', gap: '8px' }}>
                    <input
                      type="text"
                      placeholder="Enter attraction name..."
                      value={manualName}
                      onChange={(e) => setManualAddName(e.target.value)}
                      style={{ flex: 1, padding: '12px 20px', borderRadius: '12px', border: '2px solid var(--jade)', outline: 'none' }}
                      autoFocus
                    />
                    <button className="btn-primary" onClick={() => handleManualAdd(day)}>Add</button>
                    <button className="btn-secondary" onClick={() => setManualAddDay(null)}>Cancel</button>
                  </div>
                ) : (
                  <div style={{ marginTop: '12px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                    <button
                      className="btn-add-small"
                      style={{
                        padding: '14px', background: '#fff', border: '1px solid var(--line)',
                        borderRadius: '12px', color: 'var(--ink)', fontWeight: 'bold', cursor: 'pointer'
                      }}
                      onClick={() => setManualAddDay(day)}
                    >✍️ Add attraction manually</button>
                    <button
                      className="btn-add-small"
                      style={{
                        padding: '14px', background: 'var(--mint)', border: '1px dashed var(--jade)',
                        borderRadius: '12px', color: 'var(--jade-deep)', fontWeight: 'bold', cursor: 'pointer'
                      }}
                      onClick={() => navigate(`/import?tripId=${trip.id}&day=${day}`)}
                    >✨ Add via AI analysis</button>
                  </div>
                )}
              </div>
            );
          })}
        </DragDropContext>
      </div>
    </div>
  );
};

export default EditPage;
