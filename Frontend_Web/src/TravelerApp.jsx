import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import Footer from './components/Footer';
import HomePage from './pages/HomePage';
import ImportPage from './pages/ImportPage';
import AttractionPage from './pages/AttractionPage';
import ItineraryListPage from './pages/ItineraryListPage';
import ItineraryDetailPage from './pages/ItineraryDetailPage';
import MapPage from './pages/MapPage';
import EditPage from './pages/EditPage';
import ProfilePage from './pages/ProfilePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProtectedRoute from './components/ProtectedRoute';

// The traveler-facing app (header/footer chrome + its routes). Extracted from
// the old App.jsx so the admin console can live under its own top-level route
// without inheriting this chrome.
export default function TravelerApp() {
  return (
    <div className="app-container">
      <Header />
      <main>
        <div className="container">
          <Routes>
            {/* Public Routes */}
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/attraction/:name" element={<AttractionPage />} />
            <Route path="/attraction" element={<AttractionPage />} />

            {/* Protected Routes */}
            <Route path="/import" element={
              <ProtectedRoute><ImportPage /></ProtectedRoute>
            } />
            <Route path="/route" element={
              <ProtectedRoute><ItineraryListPage /></ProtectedRoute>
            } />
            <Route path="/itinerary/:id" element={
              <ProtectedRoute><ItineraryDetailPage /></ProtectedRoute>
            } />
            <Route path="/map" element={
              <ProtectedRoute><MapPage /></ProtectedRoute>
            } />
            <Route path="/edit" element={
              <ProtectedRoute><EditPage /></ProtectedRoute>
            } />
            <Route path="/profile" element={
              <ProtectedRoute><ProfilePage /></ProtectedRoute>
            } />
          </Routes>
        </div>
      </main>
      <Footer />
    </div>
  );
}
