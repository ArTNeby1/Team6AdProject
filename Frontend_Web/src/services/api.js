import axios from 'axios';

// Backend dev server runs on 8091 (8080 was occupied — see .env / application-dev.yml).
// Read from VITE_API_BASE_URL so this doesn't silently drift again if the port changes.
const API_BASE_URL = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8091'}/api/v1`;

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000, // 🟢 Increased to 60s to wait for local AI Llama3 processing
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for adding the bearer token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('loomytrip_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for handling common errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Handle unauthorized (e.g., token expired)
      localStorage.removeItem('loomytrip_token');
      localStorage.removeItem('loomytrip_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const mapApi = {
  getConfig: () => api.get('/map/config'),
  getRoute: (tripId, day) => api.get(`/trips/${tripId}/route?day=${day}`),
};

export default api;
