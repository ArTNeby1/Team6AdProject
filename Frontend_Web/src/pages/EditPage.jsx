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

  // Initialize local state from context
  useEffect(() => {
    if (trip) {
      setLocalLocations([...trip.locations]);
      setLocalDayCount(trip.dayCount || 1);
    }
  }, [trip]);

  if (!trip) return <div>Trip not found</div>;

  if (trip.status === 'FINISHED') {
    return (
      <div className="container" style={{textAlign: 'center', padding: '100px 0'}}>
        <h2>该行程已结束，无法编辑</h2>
        <button className="btn-primary" style={{marginTop: '20px'}} onClick={() => navigate(`/itinerary/${trip.id}`)}>返回详情</button>
      </div>
    );
  }

  const onDragEnd = (result) => {
    const { source, destination, draggableId } = result;
    if (!destination) return;
    if (source.droppableId === destination.droppableId && source.index === destination.index) return;

    const newLocations = Array.from(localLocations);
    const sourceIndex = newLocations.findIndex(l => l.id === draggableId);
    if (sourceIndex === -1) return;

    const [removed] = newLocations.splice(sourceIndex, 1);
    const destDay = parseInt(destination.droppableId.split('-')[1]);
    removed.day = destDay;

    // Find insertion point for local state
    const dayItems = newLocations.filter(l => l.day === destDay);
    const targetItem = dayItems[destination.index];
    const insertIdx = targetItem ? newLocations.indexOf(targetItem) : -1;

    if (insertIdx === -1) {
      // Find where to append for that day
      const lastBeforeIdx = newLocations.findLastIndex(l => l.day <= destDay);
      newLocations.splice(lastBeforeIdx + 1, 0, removed);
    } else {
      newLocations.splice(insertIdx, 0, removed);
    }

    setLocalLocations(newLocations);
  };

  const handleDelete = (id) => {
    setLocalLocations(localLocations.filter(loc => loc.id !== id));
  };

  const handleSave = () => {
    saveTripEdits(trip.id, localLocations, localDayCount);
    navigate(`/itinerary/${trip.id}`);
  };

  const handleReset = () => {
    // Re-initialize from the original trip data in context
    setLocalLocations([...trip.locations]);
    setLocalDayCount(trip.dayCount || 1);
    // Optional: add a visual feedback or just go back
    // navigate(`/itinerary/${trip.id}`);
  };

  const daysArray = Array.from({ length: localDayCount }, (_, i) => i + 1);

  return (
    <div className="edit-page">
      <header className="page-header" style={{marginBottom: '40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
        <div>
          <button className="btn-secondary" style={{padding: '6px 16px', marginBottom: '12px', fontSize: '14px'}} onClick={() => navigate(`/itinerary/${trip.id}`)}>
            ← 返回详情
          </button>
          <h1>编辑行程</h1>
          <p>{trip.title} · 共 {localDayCount} 天</p>
        </div>
        <div className="header-actions" style={{display: 'flex', gap: '12px'}}>
          <button className="btn-secondary" onClick={handleReset}>重置修改</button>
          <button className="btn-primary" onClick={handleSave}>保存修改</button>
        </div>
      </header>

      <div className="edit-container" style={{maxWidth: '800px', margin: '0 auto'}}>
        <div className="info-card" style={{background: 'var(--mint)', border: 'none', marginBottom: '32px'}}>
          <p style={{fontSize: '15px', color: 'var(--jade-deep)', fontWeight: 600}}>
            💡 提示：你可以拖动图标调整景点顺序。支持跨天拖动，点击“保存修改”后生效。
          </p>
        </div>

        <DragDropContext onDragEnd={onDragEnd}>
          {daysArray.map(day => {
            const dayLocations = localLocations.filter(loc => loc.day === day);
            return (
              <div key={day} className="edit-day-section" style={{marginBottom: '40px'}}>
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
                        minHeight: '50px',
                        background: snapshot.isDraggingOver ? 'var(--mint)' : 'transparent',
                        borderRadius: '12px',
                        transition: 'background 0.2s'
                      }}
                    >
                      {dayLocations.length === 0 ? (
                        <p style={{padding: '20px', textAlign: 'center', color: 'var(--muted)', background: '#fff', borderRadius: '12px', border: '1px dashed var(--line)'}}>
                          暂无景点，可将其他天数景点拖入此处
                        </p>
                      ) : (
                        dayLocations.map((item, idx) => (
                          <Draggable key={item.id} draggableId={item.id} index={idx}>
                            {(provided, snapshot) => (
                              <div
                                ref={provided.innerRef}
                                {...provided.draggableProps}
                                className={`edit-card ${snapshot.isDragging ? 'dragging' : ''}`}
                                style={{
                                  ...provided.draggableProps.style,
                                  marginBottom: '12px'
                                }}
                              >
                                <div className="drag-handle" {...provided.dragHandleProps}>⋮⋮</div>
                                <div className="edit-num">{idx + 1}</div>
                                <div className="edit-info">
                                  <h3>{item.name}</h3>
                                  <div className="edit-time-select">
                                    <span>{item.time || '09:00'}</span>
                                    <span>游玩 {item.duration || '1.5h'}</span>
                                  </div>
                                </div>
                                <div className="edit-actions">
                                  <button className="btn-del" onClick={() => handleDelete(item.id)}>删除</button>
                                </div>
                              </div>
                            )}
                          </Draggable>
                        ))
                      )}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>

                <button
                  className="btn-add-small"
                  style={{
                    width: '100%', padding: '12px', marginTop: '12px',
                    background: 'transparent', border: '1px dashed var(--jade)',
                    borderRadius: '12px', color: 'var(--jade-deep)', fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                  onClick={() => navigate('/import')}
                >+ 为 Day {day} 添加景点</button>
              </div>
            );
          })}
        </DragDropContext>

        <div style={{marginTop: '40px', textAlign: 'center'}}>
           <p style={{color: 'var(--muted)', fontSize: '14px'}}>需要更多天数？请在详情页点击“+”按钮。</p>
        </div>
      </div>
    </div>
  );
};

export default EditPage;
