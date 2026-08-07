import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { apiFetch } from '../api';
import { useAuth } from './AuthContext';

const TripContext = createContext();

export const useTrip = () => useContext(TripContext);

function mapApiTrip(apiTrip, previous) {
  const title = apiTrip.tripName || previous?.title || 'Untitled trip';
  const dayCount = apiTrip.durationDays || previous?.dayCount || 1;
  return {
    id: String(apiTrip.id),
    title,
    date: apiTrip.startDate || previous?.date || '',
    status: previous?.status || 'ACTIVE',
    progress: previous?.progress ?? 0,
    shortName: (title.trim().charAt(0) || 'T').toUpperCase(),
    dayCount,
    desc: previous?.desc || `${dayCount} Days`,
    color: previous?.color,
    coverImage: previous?.coverImage,
    preferences: previous?.preferences || {},
    locations: previous?.locations || [],
    updatedAt: apiTrip.updatedAt,
  };
}

export const TripProvider = ({ children }) => {
  const { user } = useAuth();
  const [trips, setTrips] = useState([]);
  const [activeTripId, setActiveTripId] = useState(null);
  const [loadingTrips, setLoadingTrips] = useState(false);
  const [tripsError, setTripsError] = useState(null);

  const refreshTrips = useCallback(async () => {
    if (!user) {
      setTrips([]);
      setActiveTripId(null);
      return [];
    }
    setLoadingTrips(true);
    setTripsError(null);
    try {
      const data = await apiFetch('/api/v1/trips');
      setTrips((prev) => {
        const prevById = Object.fromEntries(prev.map((t) => [String(t.id), t]));
        return (data || []).map((apiTrip) => mapApiTrip(apiTrip, prevById[String(apiTrip.id)]));
      });
      return data || [];
    } catch (err) {
      setTripsError(err.message || 'Failed to load trips');
      throw err;
    } finally {
      setLoadingTrips(false);
    }
  }, [user]);

  useEffect(() => {
    refreshTrips().catch(() => {});
  }, [refreshTrips]);

  useEffect(() => {
    if (!activeTripId && trips.length > 0) {
      setActiveTripId(trips[0].id);
    }
  }, [trips, activeTripId]);

  const getActiveTrip = () => trips.find((t) => String(t.id) === String(activeTripId)) || trips[0];

  const getTripById = (tripId) => trips.find((t) => String(t.id) === String(tripId));

  const addDayToTrip = (tripId) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(tripId)) return trip;
      return { ...trip, dayCount: (trip.dayCount || 1) + 1 };
    }));
  };

  const createNewTrip = async (locationNames = [], preferences = {}, options = {}) => {
    const tripName = options.tripName
      || (locationNames[0] ? `Trip: ${locationNames[0]}` : 'New trip');
    const durationDays = options.durationDays || 1;
    const startDate = options.startDate || new Date().toISOString().slice(0, 10);

    const data = await apiFetch('/api/v1/trips', {
      method: 'POST',
      body: {
        tripName,
        startDate,
        durationDays,
        travelStyle: preferences.travelStyle || null,
        preferTransport: preferences.preferTransport || null,
      },
    });

    const mapped = mapApiTrip(data, {
      preferences,
      locations: locationNames.map((name, index) => ({
        id: `loc-${Date.now()}-${index}`,
        name,
        day: 1,
        time: '09:00',
        activityType: 'Visit',
        duration: '1.5',
        transport: '🚕 TBD',
      })),
      status: 'ACTIVE',
      desc: `${durationDays} Day${durationDays > 1 ? 's' : ''}, ${locationNames.length} stops`,
    });

    setTrips((prev) => [mapped, ...prev.filter((t) => String(t.id) !== mapped.id)]);
    setActiveTripId(mapped.id);
    return mapped.id;
  };

  const addLocationToActive = (name, day = 1) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(activeTripId)) return trip;
      const newLoc = {
        id: Math.random().toString(36).substr(2, 9),
        name,
        day,
        time: '14:00',
        activityType: 'Visit',
        duration: '1',
        transport: '🚕 TBD',
      };
      return { ...trip, locations: [...trip.locations, newLoc] };
    }));
  };

  const addLocationsToTripDay = (tripId, day, locationNames) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(tripId)) return trip;
      const newLocs = locationNames.map((name, index) => ({
        id: `loc-${Date.now()}-${index}`,
        name,
        day: parseInt(day, 10),
        time: '09:00',
        activityType: 'Visit',
        duration: '1.5',
        transport: '🚕 TBD',
      }));
      return { ...trip, locations: [...trip.locations, ...newLocs] };
    }));
  };

  const removeLocationFromActive = (id) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(activeTripId)) return trip;
      return { ...trip, locations: trip.locations.filter((l) => l.id !== id) };
    }));
  };

  const moveLocation = (sourceId, destinationDay, destinationIndex) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(activeTripId)) return trip;
      const newLocations = Array.from(trip.locations);
      const sourceIndex = newLocations.findIndex((l) => l.id === sourceId);
      if (sourceIndex === -1) return trip;

      const [removed] = newLocations.splice(sourceIndex, 1);
      removed.day = parseInt(destinationDay, 10);

      const otherDayItems = newLocations.filter((l) => l.day === removed.day);
      const targetGlobalIndex = newLocations.indexOf(otherDayItems[destinationIndex]);

      if (targetGlobalIndex === -1) {
        const lastBeforeIdx = newLocations.findLastIndex((l) => l.day <= removed.day);
        newLocations.splice(lastBeforeIdx + 1, 0, removed);
      } else {
        newLocations.splice(targetGlobalIndex, 0, removed);
      }

      return { ...trip, locations: newLocations };
    }));
  };

  const saveTripEdits = (tripId, newLocations, newDayCount) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(tripId)) return trip;
      return { ...trip, locations: newLocations, dayCount: newDayCount };
    }));
  };

  const updateTripTitle = (tripId, newTitle) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(tripId)) return trip;
      return {
        ...trip,
        title: newTitle,
        shortName: (newTitle.trim().charAt(0) || 'T').toUpperCase(),
      };
    }));
  };

  const updateTripCover = (tripId, imageUrl) => {
    setTrips((prev) => prev.map((trip) => {
      if (String(trip.id) !== String(tripId)) return trip;
      return { ...trip, coverImage: imageUrl };
    }));
  };

  return (
    <TripContext.Provider value={{
      trips,
      loadingTrips,
      tripsError,
      refreshTrips,
      activeTripId,
      setActiveTripId,
      getActiveTrip,
      getTripById,
      addDayToTrip,
      addLocationToActive,
      removeLocationFromActive,
      moveLocation,
      saveTripEdits,
      createNewTrip,
      updateTripTitle,
      addLocationsToTripDay,
      updateTripCover,
    }}>
      {children}
    </TripContext.Provider>
  );
};
