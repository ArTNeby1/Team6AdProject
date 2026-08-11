import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';

const TripContext = createContext();

export const useTrip = () => useContext(TripContext);

export const TripProvider = ({ children }) => {
  const { user } = useAuth();
  const [trips, setTrips] = useState([]);
  const [activeTripId, setActiveTripId] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (user) {
      fetchTrips();
    } else {
      setTrips([]);
      setActiveTripId(null);
    }
  }, [user]);

  const fetchTrips = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/trips');
      // Normalize data from backend to frontend state
      const mappedTrips = response.data.map(t => ({
        id: t.id.toString(),
        title: t.tripName,
        date: t.startDate || 'TBD',
        status: t.status || 'NOT_STARTED',
        progress: t.status === 'FINISHED' ? 1 : (t.status === 'ACTIVE' ? 0.5 : 0),
        shortName: t.tripName.substring(0, 2).toUpperCase(),
        dayCount: t.durationDays || 1,
        coverImage: t.coverImage,
        locations: t.schedules ? t.schedules.map(s => ({
          id: s.id.toString(),
          name: s.destination.name,
          day: s.tripDay?.daySequence || 1,
          // Backend serializes LocalTime as "HH:mm:ss" — trim to "HH:mm" for display/editing
          // (EditPage.jsx's time input expects that shape too).
          time: s.startTime ? s.startTime.slice(0, 5) : '09:00',
          activityType: s.activityType || 'Visit',
          duration: s.plannedDurationMinutes ? (s.plannedDurationMinutes / 60).toFixed(1) : '1.5',
          transport: '🚕 TBD',
          lat: s.destination.latitude || 0,
          lng: s.destination.longitude || 0
        })) : []
      }));

      setTrips(mappedTrips);
      if (mappedTrips.length > 0 && !activeTripId) {
        setActiveTripId(mappedTrips[0].id);
      }
    } catch (err) {
      console.error('Failed to fetch trips:', err);
      setError(err.message || 'Failed to load trips');
    } finally {
      setLoading(false);
    }
  };

  const getActiveTrip = () => trips.find(t => t.id === activeTripId) || trips[0];
  const getTripById = (id) => trips.find(t => t.id === id?.toString());

  const deleteTrip = async (tripId) => {
    try {
      await api.delete(`/trips/${tripId}`);
      setTrips(prev => prev.filter(t => t.id !== tripId));
      if (activeTripId === tripId) {
        setActiveTripId(null);
      }
    } catch (err) {
      console.error('Failed to delete trip:', err);
      throw err;
    }
  };

  const createNewTrip = async (locationNames, preferences = {}) => {
    try {
      const response = await api.post('/trips', {
        tripName: 'Newly Imported Trip',
        startDate: new Date().toISOString().split('T')[0],
        durationDays: 1,
        travelStyle: preferences.travelStyle,
        preferTransport: preferences.preferTransport
      });

      const newTripId = response.data.id.toString();

      if (locationNames && locationNames.length > 0) {
        await addLocationsToTripDay(newTripId, 1, locationNames);
      }

      await fetchTrips();
      setActiveTripId(newTripId);
      return newTripId;
    } catch (error) {
      console.error('Failed to create trip:', error);
      throw error;
    }
  };

  const updateTripTitle = async (tripId, newTitle) => {
    try {
      await api.put(`/trips/${tripId}`, { tripName: newTitle });
      setTrips(prev => prev.map(t => t.id === tripId ? { ...t, title: newTitle } : t));
    } catch (error) {
      console.error('Failed to update title:', error);
    }
  };

  const updateTripCover = async (tripId, imageUrl) => {
    try {
      await api.put(`/trips/${tripId}`, { coverImage: imageUrl });
      setTrips(prev => prev.map(t => t.id === tripId ? { ...t, coverImage: imageUrl } : t));
    } catch (error) {
      console.error('Failed to update cover:', error);
    }
  };

  const addLocationsToTripDay = async (tripId, day, locationNames) => {
    if (!locationNames || locationNames.length === 0) return;
    try {
      // Backend expects 'day' and 'locationNames' (matching AddTripScheduleRequest)
      await api.post(`/trips/${tripId}/schedules`, {
        day: parseInt(day),
        locationNames: locationNames
      });
      await fetchTrips();
    } catch (error) {
      console.error('Failed to add locations:', error);
    }
  };

  const saveTripEdits = async (tripId, newLocations, newDayCount) => {
    try {
      setLoading(true);
      await api.put(`/trips/${tripId}`, { durationDays: newDayCount });

      // EditPage.jsx's "Delete" only drops the item from local draft state — the schedule
      // still exists in the DB until we explicitly delete it here. bulkUpdateSchedules only
      // reorders/moves schedules whose ids are IN the request, it was never told to remove
      // ones that are missing, so deleted locations kept reappearing after Save.
      const originalTrip = trips.find(t => t.id === tripId);
      const newIds = new Set(newLocations.map(loc => loc.id?.toString()));
      const removedIds = (originalTrip?.locations || [])
        .map(loc => loc.id?.toString())
        .filter(id => id && !newIds.has(id) && !id.startsWith('manual-'));
      for (const id of removedIds) {
        await api.delete(`/trips/${tripId}/schedules/${id}`);
      }

      // "Add attraction manually" (EditPage.jsx handleManualAdd) only ever created a local
      // draft item with a temp "manual-<timestamp>" id — it was never actually POSTed to the
      // backend, so it silently vanished on the next fetch even though Save looked successful.
      // Create each one now via the existing add-schedules endpoint (grouped by day, in the
      // order they appear locally — handleManualAdd always appends, so this matches), then
      // splice the real created schedule back in place of its temp entry so the reorder/time
      // pass below can pick it up like any other schedule.
      const manualItems = newLocations.filter(loc => loc.id?.toString().startsWith('manual-'));
      let resolvedLocations = newLocations;
      if (manualItems.length > 0) {
        const manualByDay = {};
        manualItems.forEach((loc) => (manualByDay[loc.day] ||= []).push(loc));

        const createdByTempId = {};
        for (const [day, locs] of Object.entries(manualByDay)) {
          const response = await api.post(`/trips/${tripId}/schedules`, {
            day: parseInt(day, 10),
            locationNames: locs.map((loc) => loc.name),
          });
          // addSchedules appends in locationNames order, so the last N schedules for this
          // day (N = locs.length) are the ones we just created, in the same order.
          const daySchedules = (response.data.schedules || [])
            .filter((s) => s.tripDay?.daySequence === parseInt(day, 10));
          const created = daySchedules.slice(-locs.length);
          locs.forEach((loc, idx) => {
            if (created[idx]) createdByTempId[loc.id] = created[idx].id.toString();
          });
        }

        resolvedLocations = newLocations.map((loc) =>
          createdByTempId[loc.id] ? { ...loc, id: createdByTempId[loc.id] } : loc
        );
      }

      // Reassign sequence per day, in array order, so drag-and-drop reordering from
      // EditPage.jsx actually survives a refresh instead of only updating durationDays.
      // startTime: EditPage.jsx's time input only ever updated local draft state — nothing
      // sent it to the backend, so an edited time always reverted on the next fetch even
      // though Save otherwise "succeeded" (the reorder/durationDays part did go through).
      const scheduleByDay = {};
      resolvedLocations
        .filter(loc => !loc.id?.toString().startsWith('manual-'))
        .forEach((loc) => {
          (scheduleByDay[loc.day] ||= []).push(loc);
        });
      const schedules = Object.entries(scheduleByDay).flatMap(([day, locs]) =>
        locs.map((loc, idx) => ({ id: parseInt(loc.id, 10), day: parseInt(day, 10), sequence: idx + 1, startTime: loc.time || null }))
      );
      if (schedules.length > 0) {
        await api.put(`/trips/${tripId}/schedules/bulk`, { schedules });
      }

      await fetchTrips();
    } catch (error) {
      console.error('Failed to save edits:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const addDayToTrip = async (tripId) => {
    const trip = trips.find(t => t.id === tripId);
    if (!trip) return;
    try {
      const newDayCount = trip.dayCount + 1;
      await api.put(`/trips/${tripId}`, { durationDays: newDayCount });
      await fetchTrips();
    } catch (error) {
      console.error('Failed to add day:', error);
    }
  };

  const fetchAttractionData = async (name) => {
    try {
      const response = await api.get(`/destinations?q=${encodeURIComponent(name)}`);
      return response.data[0];
    } catch (error) {
      console.error('Failed to fetch destination info:', error);
      return null;
    }
  };

  const removeLocationFromActive = (id) => {
    setTrips(prev => prev.map(trip => {
      if (trip.id === activeTripId) {
        return { ...trip, locations: trip.locations.filter(l => l.id !== id) };
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
      getTripById,
      addDayToTrip,
      saveTripEdits,
      createNewTrip,
      updateTripTitle,
      addLocationsToTripDay,
      updateTripCover,
      fetchAttractionData,
      loading,
      fetchTrips,
      removeLocationFromActive,
      deleteTrip
    }}>
      {children}
    </TripContext.Provider>
  );
};
