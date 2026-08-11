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
          time: s.startTime || '09:00',
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
      // Bulk update logic would go here if backend supports it
      await fetchTrips();
    } catch (error) {
      console.error('Failed to save edits:', error);
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
