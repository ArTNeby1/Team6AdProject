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

function App() {
  return (
    <div className="app-container">
      <Header />
      <main>
        <div className="container">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/import" element={<ImportPage />} />
            <Route path="/attraction" element={<AttractionPage />} />
            <Route path="/route" element={<ItineraryListPage />} />
            <Route path="/itinerary/:id" element={<ItineraryDetailPage />} />
            <Route path="/map" element={<MapPage />} />
            <Route path="/edit" element={<EditPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Routes>
        </div>
      </main>
      <Footer />
    </div>
  );
}

export default App;
