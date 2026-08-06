import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Mock User Data with ERD fields
  const MOCK_USER = {
    email: '1260892734@qq.com',
    password: '123456',
    username: 'WengYuhao',
    age: 21,
    gender: 'Male',
    travelStyle: 'Cultural',
    preferTransport: 'Public'
  };

  useEffect(() => {
    const savedUser = localStorage.getItem('loomytrip_user');
    if (savedUser) {
      setUser(JSON.parse(savedUser));
    }
    setLoading(false);
  }, []);

  const login = (email, password) => {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        if (email === MOCK_USER.email && password === MOCK_USER.password) {
          const userData = {
            ...MOCK_USER
          };
          delete userData.password;
          setUser(userData);
          localStorage.setItem('loomytrip_user', JSON.stringify(userData));
          resolve(userData);
        } else {
          reject(new Error('Incorrect email or password'));
        }
      }, 800);
    });
  };

  const register = (username, email, password, age, gender) => {
    return new Promise((resolve) => {
      setTimeout(() => {
        const userData = {
          username, email, age, gender,
          travelStyle: 'Cultural',
          preferTransport: 'Public'
        };
        setUser(userData);
        localStorage.setItem('loomytrip_user', JSON.stringify(userData));
        resolve(userData);
      }, 800);
    });
  };

  const updatePreferences = (travelStyle, preferTransport) => {
    const updatedUser = { ...user, travelStyle, preferTransport };
    setUser(updatedUser);
    localStorage.setItem('loomytrip_user', JSON.stringify(updatedUser));
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('loomytrip_user');
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, updatePreferences, loading }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
