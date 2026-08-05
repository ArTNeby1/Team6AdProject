import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Mock User Data
  const MOCK_USER = {
    email: '1260892734@qq.com',
    password: '123456',
    username: 'WengYuhao'
  };

  useEffect(() => {
    const savedUser = localStorage.getItem('yantu_user');
    if (savedUser) {
      setUser(JSON.parse(savedUser));
    }
    setLoading(false);
  }, []);

  const login = (email, password) => {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        if (email === MOCK_USER.email && password === MOCK_USER.password) {
          const userData = { email: MOCK_USER.email, username: MOCK_USER.username };
          setUser(userData);
          localStorage.setItem('yantu_user', JSON.stringify(userData));
          resolve(userData);
        } else {
          reject(new Error('邮箱或密码错误'));
        }
      }, 800);
    });
  };

  const register = (username, email, password) => {
    return new Promise((resolve) => {
      setTimeout(() => {
        const userData = { username, email };
        setUser(userData);
        localStorage.setItem('yantu_user', JSON.stringify(userData));
        resolve(userData);
      }, 800);
    });
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('yantu_user');
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, loading }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
