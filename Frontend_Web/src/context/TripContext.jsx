import React, { createContext, useContext, useState } from 'react';

const TripContext = createContext();

export const useTrip = () => useContext(TripContext);

export const TripProvider = ({ children }) => {
  const [trips, setTrips] = useState([
    {
      id: 'chiangmai-3',
      title: 'Chiang Mai 3 Days',
      date: '2026.07.15 - 2026.07.18',
      status: 'ACTIVE',
      progress: 0.6,
      shortName: 'CHIANG MAI',
      dayCount: 3,
      preferences: { travelStyle: 'Cultural', preferTransport: 'Public' },
      locations: [
        { id: '1', name: 'Wat Chedi Luang', day: 1, time: '09:30', activityType: 'Sightseeing', duration: '1.5', transport: '🚶 12 min Walk (850m)' },
        { id: '2', name: 'Wat Phra Singh', day: 1, time: '11:20', activityType: 'Visit', duration: '1', transport: '🛺 8 min Tuk-tuk (2.1km)' },
        { id: '3', name: 'Lunch at Nimman Road', day: 1, time: '12:30', activityType: 'Dining', duration: '1.5', transport: '🚕 15 min Taxi (4.2km)' },
      ]
    },
    // ... other mock trips
    {
      id: 'bali-5',
      title: 'Bali Beach',
      date: '2025.12.20',
      status: 'FINISHED',
      desc: '5 Days, 15 Stops',
      shortName: 'B',
      dayCount: 5,
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

  const createNewTrip = (locationNames, preferences = {}) => {
    const newId = `trip-${Date.now()}`;
    const newTrip = {
      id: newId,
      title: 'Newly Imported Trip',
      date: new Date().toLocaleDateString('en-US').replace(/\//g, '.'),
      status: 'NOT_STARTED',
      desc: `1 Day, ${locationNames.length} stops`,
      shortName: 'N',
      dayCount: 1,
      color: 'var(--jade)',
      preferences: preferences,
      locations: locationNames.map((name, index) => ({
        id: `ext-loc-${Date.now()}-${index}`,
        name,
        day: 1,
        time: '09:00',
        activityType: 'Visit',
        duration: '1.5',
        transport: '🚕 TBD'
      }))
    };
    setTrips(prev => [...prev, newTrip]);
    setActiveTripId(newId);
    return newId;
  };

  const addLocationToActive = (name, day = 1) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        const newLoc = {
          id: Math.random().toString(36).substr(2, 9),
          name,
          day: day,
          time: '14:00',
          activityType: 'Visit',
          duration: '1',
          transport: '🚕 TBD'
        };
        return { ...trip, locations: [...trip.locations, newLoc] };
      }
      return trip;
    }));
  };

  const addLocationsToTripDay = (tripId, day, locationNames) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        const newLocs = locationNames.map((name, index) => ({
          id: `loc-${Date.now()}-${index}`,
          name,
          day: parseInt(day),
          time: '09:00',
          activityType: 'Visit',
          duration: '1.5',
          transport: '🚕 TBD'
        }));
        return { ...trip, locations: [...trip.locations, ...newLocs] };
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
    // ... implementation logic (omitted for brevity in thinking but I should keep it correct)
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        const newLocations = Array.from(trip.locations);
        const sourceIndex = newLocations.findIndex(l => l.id === sourceId);
        if (sourceIndex === -1) return trip;

        const [removed] = newLocations.splice(sourceIndex, 1);
        removed.day = parseInt(destinationDay);

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

  const updateTripTitle = (tripId, newTitle) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        return { ...trip, title: newTitle };
      }
      return trip;
    }));
  };

  const updateTripCover = (tripId, imageUrl) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === tripId) {
        return { ...trip, coverImage: imageUrl };
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
      updateTripTitle,
      addLocationsToTripDay,
      updateTripCover
    }}>
      {children}
    </TripContext.Provider>
  );
};
