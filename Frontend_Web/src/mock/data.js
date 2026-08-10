/**
 * LoomyTrip Mock Data for Frontend Testing
 * Structures are aligned with Backend DTOs and Entities.
 */

export const MOCK_USER = {
  accessToken: "mock-jwt-token-xyz-12345",
  tokenType: "Bearer",
  userId: 1,
  username: "WengYuhao",
  email: "1260892734@qq.com",
  age: 21,
  gender: "Male",
  travelStyle: "Cultural",
  preferTransport: "Public"
};

export const MOCK_TRIPS = [
  {
    id: 1001,
    tripName: "Kyoto Autumn Escape",
    startDate: "2026-11-15",
    durationDays: 4,
    status: "ACTIVE",
    coverImage: "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=800",
    updatedAt: new Date().toISOString(),
    schedules: [
      {
        id: 5001,
        destination: {
          name: "Kinkaku-ji",
          latitude: 35.0394,
          longitude: 135.7292,
          address: "1 Kyoto-shi, Kyoto",
          category: "Temple"
        },
        startTime: "09:30",
        activityType: "Visit",
        plannedDurationMinutes: 90
      },
      {
        id: 5002,
        destination: {
          name: "Nishiki Market",
          latitude: 35.0050,
          longitude: 135.7649,
          address: "Kyoto Downtown",
          category: "Market"
        },
        startTime: "12:30",
        activityType: "Dining",
        plannedDurationMinutes: 120
      }
    ]
  },
  {
    id: 1002,
    tripName: "Singapore Weekend",
    startDate: "2026-09-20",
    durationDays: 2,
    status: "NOT_STARTED",
    coverImage: null,
    updatedAt: "2026-08-08T10:00:00Z",
    schedules: []
  }
];

export const MOCK_DESTINATIONS = [
  {
    id: 1,
    name: "Wat Chedi Luang",
    address: "103 Road King Prajadhipok, Chiang Mai",
    latitude: 18.7869,
    longitude: 98.9865,
    category: "Temple",
    description: "Built in 1411, it is the most famous temple in Chiang Mai."
  },
  {
    id: 2,
    name: "Marina Bay Sands",
    address: "10 Bayfront Ave, Singapore",
    latitude: 1.2847,
    longitude: 103.8610,
    category: "Landmark",
    description: "Iconic hotel with a rooftop infinity pool."
  }
];

export const MOCK_PLANNING_SESSION = {
  id: 2001,
  title: "AI Draft for Phuket",
  status: "ACTIVE",
  draftPlaces: [
    { id: "dp1", name: "Patong Beach", validationStatus: "VALID" },
    { id: "dp2", name: "Old Phuket Town", validationStatus: "VALID" }
  ]
};
