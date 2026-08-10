import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Restore user session on mount
  useEffect(() => {
    const savedUser = localStorage.getItem('loomytrip_user');
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch (e) {
        localStorage.removeItem('loomytrip_user');
      }
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      const response = await api.post('/auth/login', { email, password });
      const { accessToken, userId, username, email: userEmail, age, gender, travelStyle, preferTransport } = response.data;

      const userData = {
        id: userId,
        username,
        email: userEmail,
        age,
        gender,
        travelStyle,
        preferTransport
      };

      // Persistence
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
      const { accessToken, userId, username: uName, email: uEmail } = response.data;

      const userData = { id: userId, username: uName, email: uEmail, age, gender };

      localStorage.setItem('loomytrip_token', accessToken);
      localStorage.setItem('loomytrip_user', JSON.stringify(userData));

      setUser(userData);
      return userData;
    } catch (error) {
      const message = error.response?.data?.message || 'Registration failed';
      throw new Error(message);
    }
  };

  const updatePreferences = async (travelStyle, preferTransport) => {
    try {
      const updatedUser = { ...user, travelStyle, preferTransport };
      setUser(updatedUser);
      localStorage.setItem('loomytrip_user', JSON.stringify(updatedUser));

      // Optionally sync with backend
      // await api.put('/user/preferences', { travelStyle, preferTransport });
    } catch (error) {
      console.error('Failed to sync preferences:', error);
    }
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
