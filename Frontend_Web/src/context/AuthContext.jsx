import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const savedUser = localStorage.getItem('loomytrip_user');
    if (savedUser) {
      setUser(JSON.parse(savedUser));
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      const response = await api.post('/auth/login', { email, password });
      const { accessToken, userId, email: userEmail } = response.data;

      // Mock username as it's not in the DB schema
      const mockUsername = userEmail.split('@')[0];
      const userData = { id: userId, username: mockUsername, email: userEmail };

      localStorage.setItem('loomytrip_token', accessToken);
      localStorage.setItem('loomytrip_user', JSON.stringify(userData));

      setUser(userData);
      return userData;
    } catch (error) {
      const message = error.response?.data?.message || 'Incorrect email or password';
      throw new Error(message);
    }
  };

  const register = async (username, email, password, age, gender) => {
    try {
      const response = await api.post('/auth/register', {
        username,
        email,
        password,
        age,
        gender
      });
      const { accessToken, userId, username: userN, email: userEmail, travelStyle, preferTransport } = response.data;

      const userData = { id: userId, username: userN, email: userEmail, travelStyle, preferTransport };

      localStorage.setItem('loomytrip_token', accessToken);
      localStorage.setItem('loomytrip_user', JSON.stringify(userData));

      setUser(userData);
      return userData;
    } catch (error) {
      const message = error.response?.data?.message || 'Registration failed';
      throw new Error(message);
    }
  };

  // Persists to `users.travel_style` / `users.prefer_transport` via PUT /users/me/preferences
  // (was previously local-only, so it never survived a re-login or another device). Errors are
  // NOT swallowed here — callers (ProfilePage) need to know a save actually failed.
  const updatePreferences = async (travelStyle, preferTransport) => {
    const response = await api.put('/users/me/preferences', { travelStyle, preferTransport });
    const updatedUser = { ...user, travelStyle: response.data.travelStyle, preferTransport: response.data.preferTransport };
    setUser(updatedUser);
    localStorage.setItem('loomytrip_user', JSON.stringify(updatedUser));
    return updatedUser;
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('loomytrip_token');
    localStorage.removeItem('loomytrip_user');
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, updatePreferences, loading }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
