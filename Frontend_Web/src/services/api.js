import axios from 'axios';
import * as mockData from '../mock/data';

// --- CONFIGURATION ---
const USE_MOCK = true;
const API_BASE_URL = 'http://localhost:8080/api/v1';

const instance = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Helper for Mock Persistence
const getMockTrips = () => {
  const saved = localStorage.getItem('mock_trips_db');
  if (saved) return JSON.parse(saved);
  return [...mockData.MOCK_TRIPS];
};

const saveMockTrips = (trips) => {
  localStorage.setItem('mock_trips_db', JSON.stringify(trips));
};

// Request interceptor
instance.interceptors.request.use(async (config) => {
  if (USE_MOCK) {
    console.warn(`[Mock API] Intercepted ${config.method.toUpperCase()} ${config.url}`);

    let data = null;
    const url = config.url;
    const method = config.method.toUpperCase();

    if (url.includes('/auth/login') || url.includes('/auth/register')) {
      data = mockData.MOCK_USER;
    }
    else if (url.includes('/trips')) {
      let currentTrips = getMockTrips();

      if (method === 'POST' && !url.includes('/schedules')) {
        // Create new trip
        const newTrip = {
          id: Date.now(),
          tripName: config.data.tripName || "Newly Created Trip",
          startDate: config.data.startDate || "2026-08-10",
          durationDays: config.data.durationDays || 1,
          status: 'NOT_STARTED',
          coverImage: null,
          schedules: []
        };
        currentTrips = [newTrip, ...currentTrips];
        saveMockTrips(currentTrips);
        data = newTrip;
      }
      else if (method === 'POST' && url.includes('/schedules')) {
        // Add locations to a trip
        const tripId = url.split('/')[2];
        const { day, locationNames } = config.data;
        currentTrips = currentTrips.map(t => {
          if (t.id.toString() === tripId.toString()) {
            const newSchedules = locationNames.map((name, i) => ({
              id: Date.now() + i,
              destination: { name, latitude: 0, longitude: 0 },
              startTime: '09:00',
              tripDay: { daySequence: day }
            }));
            return { ...t, schedules: [...(t.schedules || []), ...newSchedules] };
          }
          return t;
        });
        saveMockTrips(currentTrips);
        data = { message: "Success" };
      }
      else if (method === 'PUT') {
        // Update trip details
        const tripId = url.split('/')[2];
        currentTrips = currentTrips.map(t => {
          if (t.id.toString() === tripId.toString()) {
            const updatedTrip = { ...t, ...config.data };
            // Ensure every schedule has a mock ID if it was just added manually
            if (updatedTrip.schedules) {
              updatedTrip.schedules = updatedTrip.schedules.map((s, i) => ({
                ...s,
                id: s.id || (Date.now() + i)
              }));
            }
            return updatedTrip;
          }
          return t;
        });
        saveMockTrips(currentTrips);
        data = currentTrips.find(t => t.id.toString() === tripId.toString());
      }
      else {
        // GET trips
        data = currentTrips;
      }
    }
    else if (url.includes('/destinations')) {
      data = mockData.MOCK_DESTINATIONS;
    }
    else if (url.includes('/planning-sessions')) {
      data = mockData.MOCK_PLANNING_SESSION;
    }

    await new Promise(r => setTimeout(r, 400));

    config.adapter = () => Promise.resolve({
      data: data,
      status: (method === 'POST') ? 201 : 200,
      statusText: 'OK',
      headers: {},
      config,
    });
  } else {
    const token = localStorage.getItem('loomytrip_token');
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!USE_MOCK && error.response?.status === 401) {
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default instance;
