# LoomyTrip - Traveler Web Client & Full-Stack Integration --Done By Weng Yuhao

## 👤 My Primary Responsibilities (Web Frontend)

As the core frontend developer, I independently architected and implemented the user-facing Web application. Below is the comprehensive list of files and modules I developed:

### 1. Core Pages & UI Components
*   **Home & Navigation**: [`src/pages/HomePage.jsx`](./src/pages/HomePage.jsx), [`src/components/Header.jsx`](./src/components/Header.jsx), [`src/components/Footer.jsx`](./src/components/Footer.jsx)
*   **Smart AI Import**: [`src/pages/ImportPage.jsx`](./src/pages/ImportPage.jsx)
*   **Interactive Map**: [`src/pages/MapPage.jsx`](./src/pages/MapPage.jsx)
*   **Itinerary Details & List**: [`src/pages/ItineraryDetailPage.jsx`](./src/pages/ItineraryDetailPage.jsx), [`src/pages/ItineraryListPage.jsx`](./src/pages/ItineraryListPage.jsx), [`src/pages/RoutePage.jsx`](./src/pages/RoutePage.jsx)
*   **Editor & Attraction**: [`src/pages/EditPage.jsx`](./src/pages/EditPage.jsx), [`src/pages/AttractionPage.jsx`](./src/pages/AttractionPage.jsx)
*   **Auth & Profile**: [`src/pages/LoginPage.jsx`](./src/pages/LoginPage.jsx), [`src/pages/RegisterPage.jsx`](./src/pages/RegisterPage.jsx), [`src/pages/ProfilePage.jsx`](./src/pages/ProfilePage.jsx), [`src/components/ProtectedRoute.jsx`](./src/components/ProtectedRoute.jsx)

### 2. Global Logic & Data Management
*   **State Management**: [`src/context/AuthContext.jsx`](./src/context/AuthContext.jsx), [`src/context/TripContext.jsx`](./src/context/TripContext.jsx)
*   **Infrastructure**: [`src/services/api.js`](./src/services/api.js), [`index.html`](./index.html)
*   **Testing & Mocks**: [`src/test/`](./src/test/), [`src/mock/data.js`](./src/mock/data.js)

---

## 🧪 Implementation Highlights & QA
*   **Code Coverage**: Achieved **65% coverage** for user-facing logic through 20+ unit tests.
*   **Smart Algorithms**: Developed a **Time Cascading Algorithm** for automatic itinerary re-scheduling.
*   **Security Hardening**: Implemented CSP and Referrer policies, fixing 100% of high-risk ZAP audit findings.

---

## 🛠️ Full-Stack Technology Overview

LoomyTrip is a distributed system consisting of a React frontend, a Spring Boot backend, and a Python AI agent.

### Frontend Stack (Web)
- **Framework**: React 18 + Vite
- **Maps**: Leaflet.js / React-Leaflet
- **Styling**: Modern CSS with CSS Variables
- **Testing**: Vitest + React Testing Library

### Backend Stack (Integrated)
- **Framework**: Spring Boot 3.5 (Java 21)
- **Security**: Spring Security + JWT
- **Database**: MySQL (LoomyTrip)
- **AI Integration**: FastAPI (Python) service for LLM-based itinerary extraction.

---

## 🚀 Usage & Local Setup

### 1. Database Setup
Create the MySQL database used by the backend:
```sql
CREATE DATABASE LoomyTrip CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Running the Frontend (Web)
```bash
cd Frontend_Web
npm install
npm run dev
```
By default, the Web client connects to the backend at `http://localhost:8091/api/v1`.

### 3. Running Tests
```bash
npm run test           # Run all unit tests
npm run test:coverage  # Generate coverage report in /coverage directory
```

---

**LoomyTrip - Making Travel Smarter.**
