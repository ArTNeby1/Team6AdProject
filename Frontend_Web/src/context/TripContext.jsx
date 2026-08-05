import React, { createContext, useContext, useState } from 'react';

const TripContext = createContext();

export const useTrip = () => useContext(TripContext);

export const TripProvider = ({ children }) => {
  const [trips, setTrips] = useState([
    {
      id: 'chiangmai-3',
      title: '清迈 3 日',
      date: '2026.07.15 - 2026.07.18',
      status: 'ACTIVE',
      progress: 0.6,
      shortName: 'CHIANG MAI',
      dayCount: 3,
      locations: [
        { id: '1', name: '契迪龙寺', day: 1, time: '09:30', duration: '1.5h', transport: '🚶 步行 12 分钟 (850m)' },
        { id: '2', name: '帕辛寺', day: 1, time: '11:20', duration: '1h', transport: '🛺 嘟嘟车 8 分钟 (2.1km)' },
        { id: '3', name: '宁曼路午餐', day: 1, time: '12:30', duration: '1.5h', transport: '🚕 打车 15 分钟 (4.2km)' },
      ]
    },
    {
      id: 'new-trip',
      title: '新导入的行程',
      date: '2026.10.10',
      status: 'NOT_STARTED',
      desc: '1 天 4 站',
      shortName: '新',
      dayCount: 1,
      color: 'var(--jade)',
      locations: []
    },
    {
      id: 'bali-5',
      title: '巴厘岛海滩',
      date: '2025.12.20',
      status: 'FINISHED',
      desc: '5 天 15 站',
      shortName: '巴',
      dayCount: 5,
      color: 'var(--muted)',
      locations: []
    },
    {
      id: 'singapore-4',
      title: '新加坡 4 日',
      date: '2026.05.01',
      status: 'FINISHED',
      desc: '4 天 12 站',
      shortName: '新',
      dayCount: 4,
      color: 'var(--muted)',
      locations: []
    },
    {
      id: 'bangkok-2',
      title: '曼谷探店',
      date: '2026.03.12',
      status: 'FINISHED',
      desc: '2 天 8 站',
      shortName: '曼',
      dayCount: 2,
      color: 'var(--muted)',
      locations: []
    }
  ]);

  const [activeTripId, setActiveTripId] = useState('chiangmai-3');

  const getActiveTrip = () => trips.find(t => t.id === activeTripId) || trips[0];

  const addDayToTrip = (tripId) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        return { ...trip, dayCount: (trip.dayCount || 1) + 1 };
      }
      return trip;
    }));
  };

  const addLocationToActive = (name, day = 1) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        const newLoc = {
          id: Math.random().toString(36).substr(2, 9),
          name,
          day: day,
          time: '14:00',
          duration: '1h',
          transport: '🚕 待定'
        };
        return { ...trip, locations: [...trip.locations, newLoc] };
      }
      return trip;
    }));
  };

  const removeLocationFromActive = (id) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        return { ...trip, locations: trip.locations.filter(l => l.id !== id) };
      }
      return trip;
    }));
  };

  const moveLocation = (sourceId, destinationDay, destinationIndex) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        const newLocations = Array.from(trip.locations);
        const sourceIndex = newLocations.findIndex(l => l.id === sourceId);
        if (sourceIndex === -1) return trip;

        const [removed] = newLocations.splice(sourceIndex, 1);
        removed.day = parseInt(destinationDay);

        // Logic to insert into the correct relative position for that day
        const otherDayItems = newLocations.filter(l => l.day === removed.day);
        const targetGlobalIndex = newLocations.indexOf(otherDayItems[destinationIndex]);

        if (targetGlobalIndex === -1) {
          const lastBeforeIdx = newLocations.findLastIndex(l => l.day <= removed.day);
          newLocations.splice(lastBeforeIdx + 1, 0, removed);
        } else {
          newLocations.splice(targetGlobalIndex, 0, removed);
        }

        return { ...trip, locations: newLocations };
      }
      return trip;
    }));
  };

  const saveTripEdits = (tripId, newLocations, newDayCount) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        return { ...trip, locations: newLocations, dayCount: newDayCount };
      }
      return trip;
    }));
  };

  const createNewTrip = (locationNames) => {
    const newId = `trip-${Date.now()}`;
    const newTrip = {
      id: newId,
      title: '新导入的行程',
      date: new Date().toLocaleDateString('zh-CN').replace(/\//g, '.'),
      status: 'NOT_STARTED',
      desc: `1 天 ${locationNames.length} 站`,
      shortName: '新',
      dayCount: 1,
      color: 'var(--jade)',
      locations: locationNames.map((name, index) => ({
        id: `ext-loc-${Date.now()}-${index}`,
        name,
        day: 1,
        time: '09:00',
        duration: '1.5h',
        transport: '🚕 待定'
      }))
    };
    setTrips(prev => [...prev, newTrip]);
    setActiveTripId(newId);
    return newId;
  };

  const updateTripTitle = (tripId, newTitle) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        return { ...trip, title: newTitle };
      }
      return trip;
    }));
  };

  return (
    <TripContext.Provider value={{
      trips,
      activeTripId,
      setActiveTripId,
      getActiveTrip,
      addDayToTrip,
      addLocationToActive,
      removeLocationFromActive,
      moveLocation,
      saveTripEdits,
      createNewTrip,
      updateTripTitle
    }}>
      {children}
    </TripContext.Provider>
  );
};
