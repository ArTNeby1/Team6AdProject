import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTrip } from '../context/TripContext';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

const EditPage = () => {
  const navigate = useNavigate();
  const { getActiveTrip, saveTripEdits, loading } = useTrip();
  const trip = getActiveTrip();

  const [localLocations, setLocalLocations] = useState([]);
  const [localDayCount, setLocalDayCount] = useState(1);
  const [manualAddDay, setManualAddDay] = useState(null);
  const [manualName, setManualAddName] = useState('');

  const sortByTime = (items) => {
    return [...items].sort((a, b) => {
      if (a.day !== b.day) return a.day - b.day;
      return (a.time || '').localeCompare(b.time || '');
    });
  };

  useEffect(() => {
    if (trip) {
      setLocalLocations(sortByTime([...trip.locations]));
      setLocalDayCount(trip.dayCount || 1);
    }
  }, [trip]);

  if (!trip) return <div>Trip not found</div>;

  const onDragEnd = (result) => {
    const { source, destination, draggableId } = result;
    if (!destination) return;
    if (source.droppableId === destination.droppableId && source.index === destination.index) return;

    const newFullList = Array.from(localLocations);
    const sourceIdx = newFullList.findIndex(l => l.id === draggableId);
    const [movedItem] = newFullList.splice(sourceIdx, 1);

    const destDay = parseInt(destination.droppableId.split('-')[1]);
    movedItem.day = destDay;

    // Logic to re-assign times based on position slots
    const dayItems = newFullList.filter(l => l.day === destDay);
    dayItems.splice(destination.index, 0, movedItem);

    setLocalLocations(sortByTime(newFullList));
  };

  const handleSave = async () => {
    await saveTripEdits(trip.id, localLocations, localDayCount);
    navigate(`/itinerary/${trip.id}`);
  };

  if (loading) return <div style={{ padding: '100px', textAlign: 'center' }}>Saving changes...</div>;

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
          <button className="btn-primary" onClick={handleSave}>Save Changes</button>
        </div>
      </header>

      <div className="edit-container" style={{maxWidth: '900px', margin: '0 auto'}}>
        <DragDropContext onDragEnd={onDragEnd}>
          {Array.from({ length: localDayCount }).map((_, i) => {
            const day = i + 1;
            const dayLocations = localLocations.filter(loc => loc.day === day);
            return (
              <div key={day} className="edit-day-section" style={{marginBottom: '48px'}}>
                <h2>Day {day}</h2>
                <Droppable droppableId={`day-${day}`}>
                  {(provided) => (
                    <div ref={provided.innerRef} {...provided.droppableProps} className="edit-list">
                      {dayLocations.map((item, idx) => (
                        <Draggable key={item.id} draggableId={item.id} index={idx}>
                          {(provided) => (
                            <div ref={provided.innerRef} {...provided.draggableProps} className="edit-card">
                              <div className="drag-handle" {...provided.dragHandleProps}>⋮⋮</div>
                              <div className="edit-num">{idx + 1}</div>
                              <div className="edit-info">
                                <h3>{item.name}</h3>
                                <p>{item.time} | {item.activityType}</p>
                              </div>
                            </div>
                          )}
                        </Draggable>
                      ))}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>
              </div>
            );
          })}
        </DragDropContext>
      </div>
    </div>
  );
};

export default EditPage;
