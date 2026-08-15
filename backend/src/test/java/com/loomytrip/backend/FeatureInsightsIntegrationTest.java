package com.loomytrip.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loomytrip.backend.security.JwtService;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureInsightsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    private long travelerId;
    private String travelerToken;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM user_notification");
        jdbc.update("DELETE FROM trip_transport");
        jdbc.update("DELETE FROM trip_schedule");
        jdbc.update("DELETE FROM trip_day");
        jdbc.update("DELETE FROM planning_session");
        jdbc.update("DELETE FROM trip");
        jdbc.update("DELETE FROM destination");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM admin");

        jdbc.update(
                "INSERT INTO users (email, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                "traveler@example.com", "hash", "traveler", Timestamp.from(Instant.now())
        );
        travelerId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "traveler@example.com"
        );
        travelerToken = jwtService.generateToken(travelerId, "traveler@example.com");
    }

    @Test
    void notifications_areScopedToUserAndCanBeMarkedRead() throws Exception {
        jdbc.update(
                "INSERT INTO user_notification (user_id, type, title, body, created_at) VALUES (?, ?, ?, ?, ?)",
                travelerId, "IMPORT_COMPLETE", "Your itinerary import is ready", "Review your draft.",
                Timestamp.from(Instant.now())
        );
        Long notificationId = jdbc.queryForObject("SELECT id FROM user_notification", Long.class);

        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true").header("Authorization", bearer(travelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("IMPORT_COMPLETE"))
                .andExpect(jsonPath("$[0].readAt").isEmpty());

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(travelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true").header("Authorization", bearer(travelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tripSummaryAggregatesStopsCategoriesAndPersistedTravel() throws Exception {
        jdbc.update(
                "INSERT INTO destination (name, latitude, longitude, category, address) VALUES (?, ?, ?, ?, ?)",
                "Gardens by the Bay", 1.2816, 103.8636, "attraction", "Singapore"
        );
        long attractionId = jdbc.queryForObject("SELECT id FROM destination", Long.class);
        jdbc.update(
                "INSERT INTO destination (name, latitude, longitude, category, address) VALUES (?, ?, ?, ?, ?)",
                "Satay by the Bay", 1.2800, 103.8600, "restaurant", "Singapore"
        );
        long restaurantId = jdbc.queryForObject("SELECT MAX(id) FROM destination", Long.class);
        jdbc.update(
                "INSERT INTO trip (user_id, trip_name, start_date, duration_days, is_favorite, created_at, updated_at) "
                        + "VALUES (?, ?, CURRENT_DATE, 1, FALSE, ?, ?)",
                travelerId, "Singapore day", Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );
        long tripId = jdbc.queryForObject("SELECT id FROM trip", Long.class);
        jdbc.update("INSERT INTO trip_day (trip_id, day_sequence) VALUES (?, 1)", tripId);
        long dayId = jdbc.queryForObject("SELECT id FROM trip_day", Long.class);
        jdbc.update(
                "INSERT INTO trip_schedule (trip_day_id, destination_id, sequence, is_locked) VALUES (?, ?, 1, FALSE)",
                dayId, attractionId
        );
        long firstScheduleId = jdbc.queryForObject("SELECT id FROM trip_schedule", Long.class);
        jdbc.update(
                "INSERT INTO trip_schedule (trip_day_id, destination_id, sequence, is_locked) VALUES (?, ?, 2, FALSE)",
                dayId, restaurantId
        );
        long secondScheduleId = jdbc.queryForObject("SELECT MAX(id) FROM trip_schedule", Long.class);
        jdbc.update(
                "INSERT INTO trip_transport (trip_day_id, prev_schedule_id, next_schedule_id, transport_type, distance_km, duration_minutes) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                dayId, firstScheduleId, secondScheduleId, "driving", 2.5, 12
        );

        mockMvc.perform(get("/api/v1/trips/{id}/summary", tripId).header("Authorization", bearer(travelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlaces").value(2))
                .andExpect(jsonPath("$.uniqueDestinations").value(2))
                .andExpect(jsonPath("$.totalDistanceKm").value(2.5))
                .andExpect(jsonPath("$.totalTravelDurationMinutes").value(12))
                .andExpect(jsonPath("$.metricsComplete").value(true))
                .andExpect(jsonPath("$.categoryCounts.length()").value(2));

        // A duplicate row has the right count but is not a valid route leg. The dashboard
        // must validate the actual schedule pair rather than comparing only leg counts.
        jdbc.update(
                "INSERT INTO trip_transport (trip_day_id, prev_schedule_id, next_schedule_id, transport_type, distance_km, duration_minutes) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                dayId, firstScheduleId, secondScheduleId, "driving", 2.5, 12
        );
        mockMvc.perform(get("/api/v1/trips/{id}/summary", tripId).header("Authorization", bearer(travelerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsComplete").value(false))
                .andExpect(jsonPath("$.warnings").isNotEmpty());
    }

    @Test
    void analyticsOverviewRequiresAdminAndReturnsMetrics() throws Exception {
        jdbc.update(
                "INSERT INTO admin (email, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                "admin@example.com", "hash", "admin", Timestamp.from(Instant.now())
        );
        long adminId = jdbc.queryForObject("SELECT id FROM admin", Long.class);
        String adminToken = jwtService.generateAdminToken(adminId, "admin@example.com");

        mockMvc.perform(get("/api/v1/admin/analytics/overview").header("Authorization", bearer(travelerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/analytics/overview?bucket=day")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.definitions.activeUsers").isNotEmpty())
                .andExpect(jsonPath("$.importStats.sessionsStarted").value(0));

        mockMvc.perform(get("/api/v1/admin/analytics/overview?limit=0")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/analytics/overview?from=2024-01-01&to=2026-01-01")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmIsBlockedWhileImportIsStillProcessing() throws Exception {
        jdbc.update(
                "INSERT INTO planning_session (user_id, title, initial_brief, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                travelerId, "Processing import", "Day 1 Gardens. Day 2 Sentosa.", "PROCESSING",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );
        long sessionId = jdbc.queryForObject("SELECT id FROM planning_session", Long.class);

        mockMvc.perform(post("/api/v1/planning-sessions/{id}/confirm", sessionId)
                        .header("Authorization", bearer(travelerToken)))
                .andExpect(status().isConflict());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
