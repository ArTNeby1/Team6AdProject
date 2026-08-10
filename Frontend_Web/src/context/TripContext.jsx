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

  // Fetch all trips from Backend when user is logged in
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
      // Data mapping from Backend response to Frontend state structure
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
    } catch (error) {
      console.error('Failed to fetch trips from real backend:', error);
    } finally {
      setLoading(false);
    }
  };

  const getActiveTrip = () => trips.find(t => t.id === activeTripId) || trips[0];

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
        await api.post(`/trips/${newTripId}/schedules`, {
          day: 1,
          locationNames
        });
      }

      await fetchTrips();
      setActiveTripId(newTripId);
      return newTripId;
    } catch (error) {
      console.error('Failed to create trip on backend:', error);
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
      console.error('Failed to add locations to backend:', error);
    }
  };

  const saveTripEdits = async (tripId, newLocations, newDayCount) => {
    try {
      setLoading(true);
      // Map frontend 'locations' back to backend 'schedules' format for the API call
      const schedules = newLocations.map(loc => ({
        id: loc.id.startsWith('manual-') ? null : parseInt(loc.id),
        startTime: loc.time,
        activityType: loc.activityType,
        plannedDurationMinutes: parseFloat(loc.duration) * 60,
        tripDay: { daySequence: loc.day },
        destination: { name: loc.name } // Simplified for mock/update
      }));

      await api.put(`/trips/${tripId}`, {
        durationDays: newDayCount,
        schedules: schedules
      });

      await fetchTrips();
    } catch (error) {
      console.error('Failed to save edits to backend:', error);
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
    // Note: Should call DELETE /api/v1/schedules/{id} in next iteration
  };

  return (
    <TripContext.Provider value={{
      trips,
      activeTripId,
      setActiveTripId,
      getActiveTrip,
      addDayToTrip,
      saveTripEdits,
      createNewTrip,
      updateTripTitle,
      addLocationsToTripDay,
      updateTripCover,
      fetchAttractionData,
      loading,
      fetchTrips,
      removeLocationFromActive
    }}>
      {children}
    </TripContext.Provider>
  );
};
