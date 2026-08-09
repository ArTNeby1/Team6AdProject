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
      const { accessToken, userId, username, email: userEmail } = response.data;

      const userData = { id: userId, username, email: userEmail };

      // Save to local storage
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
      const { accessToken, userId, username: userN, email: userEmail } = response.data;

      const userData = { id: userId, username: userN, email: userEmail };

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
