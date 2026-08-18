import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

const addMinutes = (time, minutes) => {
  // handleTimeChange re-cascades on every keystroke of its HH:mm mask, so this sees
  // half-typed values like "1" (no colon yet, no minutes half at all) mid-edit — fall back
  // to 0 for whichever half is missing/non-numeric instead of propagating NaN into every
  // stop after the one being typed into.
  const [hRaw, mRaw] = (time || '09:00').split(':');
  const h = Number(hRaw) || 0;
  const m = Number(mRaw) || 0;
  const total = ((h * 60 + m + minutes) % 1440 + 1440) % 1440;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
};

// Re-sorts one day's stops into real chronological order and re-cascades times around any
// timeLocked anchors (set by handleTimeChange whenever the user hand-types a time — mirrors
// trip_schedule.is_locked). Used after both a drag-and-drop reorder and a direct time edit:
// locked stops sort by their own real time (so retyping stop 1 from 09:00 to 14:00 while
// stop 2 is locked at 12:00 moves stop 1 to *after* stop 2, not just past it visually while
// staying first in the list) — DEFAULT_VISIT_SLOT_MINUTES on the backend matches the 90min
// cascade step used here.
//
// Unlocked stops don't carry a "real" time of their own (theirs is just whatever the last
// cascade happened to leave them with), so they're not sorted by that value directly —
// each one inherits the time of whichever locked stop precedes it in the current order (or
// '00:00' if none does yet) as its sort key instead, then a *stable* sort (spec-guaranteed
// in all modern JS engines) keeps same-key stops in their existing relative order. That
// means ordinary drag-and-drop among unlocked stops is completely unaffected by this
// function — they just "float" along with whichever locked anchor they were already
// sitting after — and only locked stops actually get reordered by real time.
const recomputeDayOrder = (items) => {
  let precedingLockedTime = '00:00';
  const withSortKey = items.map((item) => {
    if (item.timeLocked) {
      precedingLockedTime = item.time || '00:00';
      return { item, sortKey: item.time || '00:00' };
    }
    return { item, sortKey: precedingLockedTime };
  });
  withSortKey.sort((a, b) => a.sortKey.localeCompare(b.sortKey));

  let cursor = '09:00';
  return withSortKey.map(({ item }) => {
    if (item.timeLocked) {
      cursor = addMinutes(item.time, 90);
      return item;
    }
    const time = cursor;
    cursor = addMinutes(time, 90);
    return { ...item, time };
  });
};

const EditPage = () => {
  const navigate = useNavigate();
  const { getActiveTrip, saveTripEdits, deleteDay } = useTrip();
  const trip = getActiveTrip();

  // Local state for "Draft" mode
  const [localLocations, setLocalLocations] = useState([]);
  const [localDayCount, setLocalDayCount] = useState(1);
  const [manualAddDay, setManualAddDay] = useState(null);
  const [manualName, setManualAddName] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [deletingDay, setDeletingDay] = useState(null);

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

    // Every item here is the *same object reference* as in trip.locations (sortByTime only
    // spreads the array, not each item — see the useEffect above and handleReset below).
    // Mutating a field on one of these in place — as this function used to do — silently
    // corrupts trip.locations too, since it's literally the same object in memory. That
    // makes "Reset Changes" a no-op after any drag: it resets to trip.locations, which by
    // then already matches the dragged state. Everything below must build new objects
    // instead of assigning onto existing ones.
    const newFullList = Array.from(localLocations);
    const sourceIdx = newFullList.findIndex(l => l.id === draggableId);
    const [originalMovedItem] = newFullList.splice(sourceIdx, 1);
    const movedItem = { ...originalMovedItem, day: destDay };

    const dayGroups = {};
    for (let d = 1; d <= localDayCount; d++) {
        dayGroups[d] = newFullList.filter(l => l.day === d);
    }

    // Insert into target day helper
    dayGroups[destDay].splice(destination.index, 0, movedItem);

    // Re-sort + re-cascade the affected day(s) — see recomputeDayOrder's own comment for
    // why this beats both a plain positional cascade (never gave a moved stop its own real
    // time) and a naive "just reorder, don't retime" drag (leaves locked stops wherever the
    // drag put them even when their real time now puts them chronologically out of order).
    dayGroups[destDay] = recomputeDayOrder(dayGroups[destDay]);
    if (sourceDay !== destDay) {
        dayGroups[sourceDay] = recomputeDayOrder(dayGroups[sourceDay]);
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

    // A direct edit means the user picked this time on purpose — lock it (mirrors
    // trip_schedule.is_locked) so drag-and-drop's auto-cascade treats it as an anchor
    // instead of silently overwriting it. It can also jump the stop chronologically past
    // its neighbours (e.g. typing 14:00 on a stop that was sitting before a 12:00-locked
    // one) — re-run the same recompute drag uses so the list re-sorts into real time
    // order right away, not just whenever the user happens to drag something next.
    setLocalLocations(prev => {
      const targetItem = prev.find(item => item.id === id);
      if (!targetItem) return prev;
      const withEdit = prev.map(item =>
        item.id === id ? { ...item, time: formatted, timeLocked: true } : item
      );
      const result = [];
      for (let d = 1; d <= localDayCount; d++) {
        const dayItems = withEdit.filter(item => item.day === d);
        result.push(...(d === targetItem.day ? recomputeDayOrder(dayItems) : dayItems));
      }
      return result;
    });
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

  // Unlike everything else on this page, deletion isn't a local draft edit that waits for
  // Save — the backend owns the renumbering (later days shift down, duration_days shrinks),
  // so this hits the API immediately and refetches, same reasoning as EditPage's existing
  // per-location delete-via-Save flow but without a draft step in between (there's no sane
  // way to preview "day 4 becomes day 3" purely in local state without duplicating that
  // logic here and risking it drifting from the backend's).
  const handleDeleteDay = async (day) => {
    if (localDayCount <= 1) return;
    if (!window.confirm(`Delete Day ${day}? Every location on it will be removed, and later days will shift down to fill the gap.`)) {
      return;
    }
    setDeletingDay(day);
    try {
      await deleteDay(trip.id, day);
    } catch (error) {
      alert(error.response?.data?.message || 'Failed to delete day, please try again');
    } finally {
      setDeletingDay(null);
    }
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
                <h2 style={{fontSize: '20px', marginBottom: '16px', borderBottom: '2px solid var(--line-soft)', paddingBottom: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                  Day {day}
                  {localDayCount > 1 && (
                    <button
                      className="btn-del"
                      style={{fontSize: '13px', fontWeight: 'normal', border: 'none', background: 'none', cursor: 'pointer'}}
                      onClick={() => handleDeleteDay(day)}
                      disabled={deletingDay === day}
                    >
                      {deletingDay === day ? 'Deleting...' : '🗑️ Delete Day'}
                    </button>
                  )}
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
