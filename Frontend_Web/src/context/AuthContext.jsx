import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiFetch, TRAVELER_TOKEN_KEY, getTravelerToken } from '../api';

const AuthContext = createContext();

const USER_KEY = 'loomytrip_user';

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = getTravelerToken();
    const savedUser = localStorage.getItem(USER_KEY);
    if (token && savedUser) {
      setUser(JSON.parse(savedUser));
    } else {
      localStorage.removeItem(TRAVELER_TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    }
    setLoading(false);
  }, []);

  const persistSession = (data, extras = {}) => {
    const userData = {
      userId: data.userId,
      email: data.email,
      username: extras.username || data.email?.split('@')[0] || 'Traveler',
      age: extras.age ?? null,
      gender: extras.gender ?? null,
      travelStyle: extras.travelStyle || 'Cultural',
      preferTransport: extras.preferTransport || 'Public',
    };
    localStorage.setItem(TRAVELER_TOKEN_KEY, data.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(userData));
    setUser(userData);
    return userData;
  };

  const login = async (email, password) => {
    const data = await apiFetch('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });
    return persistSession(data);
  };

  const register = async (username, email, password, age, gender) => {
    const data = await apiFetch('/api/v1/auth/register', {
      method: 'POST',
      body: {
        email,
        password,
        age: Number.isFinite(age) ? age : null,
        gender: gender || null,
      },
      auth: false,
    });
    return persistSession(data, { username, age, gender });
  };

  const updatePreferences = (travelStyle, preferTransport) => {
    const updatedUser = { ...user, travelStyle, preferTransport };
    setUser(updatedUser);
    localStorage.setItem(USER_KEY, JSON.stringify(updatedUser));
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem(TRAVELER_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, updatePreferences, loading }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
