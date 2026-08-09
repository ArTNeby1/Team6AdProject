import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from './AuthContext';

const TripContext = createContext();

export const useTrip = () => useContext(AuthContext);

export const TripProvider = ({ children }) => {
  const { user } = useAuth();
  const [trips, setTrips] = useState([]);
  const [activeTripId, setActiveTripId] = useState(null);
  const [loading, setLoading] = useState(false);

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
    try {
      const response = await api.get('/trips');
      const mappedTrips = response.data.map(t => ({
        id: t.id.toString(),
        title: t.tripName,
        date: t.startDate || 'TBD',
        status: t.status || 'NOT_STARTED',
        progress: 0,
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
          lat: s.destination.latitude,
          lng: s.destination.longitude
        })) : []
      }));
      setTrips(mappedTrips);
      if (mappedTrips.length > 0 && !activeTripId) {
        setActiveTripId(mappedTrips[0].id);
      }
    } catch (error) {
      console.error('Failed to fetch trips:', error);
    } finally {
      setLoading(false);
    }
  };

  const getActiveTrip = () => trips.find(t => t.id === activeTripId) || trips[0];

  const createNewTrip = async (locationNames, preferences = {}) => {
    try {
      const response = await api.post('/trips', {
        tripName: 'New Trip',
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
    try {
      await api.post(`/trips/${tripId}/schedules`, {
        day: parseInt(day),
        locationNames
      });
      await fetchTrips();
    } catch (error) {
      console.error('Failed to add locations:', error);
    }
  };

  const saveTripEdits = async (tripId, newLocations, newDayCount) => {
    try {
      // In a full sync, you might call multiple endpoints or a bulk update
      // For now, updating the metadata
      await api.put(`/trips/${tripId}`, { durationDays: newDayCount });

      // Ideally, a specialized bulk schedule update endpoint would be called here
      // await api.put(`/trips/${tripId}/schedules/bulk`, { schedules: newLocations });

      await fetchTrips();
    } catch (error) {
      console.error('Failed to save edits:', error);
    }
  };

  const addDayToTrip = async (tripId) => {
    const trip = trips.find(t => t.id === tripId);
    if (!trip) return;
    const newDayCount = trip.dayCount + 1;
    try {
      await api.put(`/trips/${tripId}`, { durationDays: newDayCount });
      await fetchTrips();
    } catch (error) {
      console.error('Failed to add day:', error);
    }
  };

  const fetchAttractionData = async (name) => {
    try {
      const response = await api.get(`/destinations?q=${encodeURIComponent(name)}`);
      return response.data[0]; // Return the first match
    } catch (error) {
      console.error('Failed to fetch attraction:', error);
      return null;
    }
  };

  return (
    <TripContext.Provider value={{
      trips,
      activeTripId,
      setActiveTripId,
      getActiveTrip,
      addDayToTrip,
      createNewTrip,
      updateTripTitle,
      addLocationsToTripDay,
      updateTripCover,
      fetchAttractionData,
      saveTripEdits,
      loading,
      fetchTrips
    }}>
      {children}
    </TripContext.Provider>
  );
};
