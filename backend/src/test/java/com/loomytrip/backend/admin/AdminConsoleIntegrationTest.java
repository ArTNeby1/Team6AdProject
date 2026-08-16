package com.loomytrip.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomytrip.backend.security.JwtService;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end coverage for the S1 admin console: admin login issues an
 * admin-typed JWT, that token can read the traveler user list, the list never
 * leaks password hashes, and neither anonymous nor traveler-typed tokens can
 * reach admin endpoints (RBAC).
 *
 * <p>Rows are seeded via SQL because {@code created_at} is populated by a DB
 * default (mapped {@code insertable = false}); the HTTP layer under test is the
 * real thing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleIntegrationTest {

    private static final String ADMIN_EMAIL = "root@loomytrip.local";
    private static final String ADMIN_PASSWORD = "Sup3r@Secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        // Same FK-safe cleanup order as FeatureInsightsIntegrationTest — both classes share
        // the same @SpringBootTest H2 context/DB (Spring caches it by config signature), so
        // a trip row left behind by whichever integration test class runs first blocks this
        // class's own `DELETE FROM users` with a FK violation otherwise (trip.user_id).
        jdbc.update("DELETE FROM agent_validation_log");
        jdbc.update("DELETE FROM user_notification");
        jdbc.update("DELETE FROM trip_transport");
        jdbc.update("DELETE FROM trip_schedule");
        jdbc.update("DELETE FROM trip_day");
        jdbc.update("DELETE FROM planning_session");
        jdbc.update("DELETE FROM trip");
        jdbc.update("DELETE FROM destination");
        jdbc.update("DELETE FROM admin");
        jdbc.update("DELETE FROM users");

        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO admin (email, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "super_admin", now);

        for (int i = 1; i <= 3; i++) {
            jdbc.update(
                    "INSERT INTO users (email, password_hash, role, created_at) VALUES (?, ?, ?, ?)",
                    "traveler" + i + "@example.com", passwordEncoder.encode("pw" + i), "traveler", now);
        }
    }

    @Test
    void adminLogin_withValidCredentials_returnsBearerTokenAndRole() throws Exception {
        MvcResult result = mockMvc.perform(login(ADMIN_EMAIL, ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("super_admin"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertThat(tokenFrom(result)).isNotBlank();
    }

    @Test
    void adminLogin_withWrongPassword_isUnauthorized() throws Exception {
        mockMvc.perform(login(ADMIN_EMAIL, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void userList_withoutToken_isRejected() throws Exception {
        int statusCode = mockMvc.perform(get("/api/v1/admin/users"))
                .andReturn().getResponse().getStatus();
        // Anonymous access to a protected route is denied (401 or 403 depending
        // on the entry point); either is a valid "not allowed".
        assertThat(statusCode).isIn(401, 403);
    }

    @Test
    void userList_withAdminToken_returnsUsersWithoutPasswordHash() throws Exception {
        String token = tokenFrom(mockMvc.perform(login(ADMIN_EMAIL, ADMIN_PASSWORD)).andReturn());

        String body = mockMvc.perform(get("/api/v1/admin/users?page=0&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].email").value("traveler1@example.com"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("passwordHash").doesNotContain("password_hash");
    }

    @Test
    void userList_withEmailQuery_filtersResults() throws Exception {
        String token = tokenFrom(mockMvc.perform(login(ADMIN_EMAIL, ADMIN_PASSWORD)).andReturn());

        mockMvc.perform(get("/api/v1/admin/users?q=traveler2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("traveler2@example.com"));
    }

    @Test
    void userList_withTravelerToken_isForbidden() throws Exception {
        // A valid but traveler-typed token must not reach admin RBAC-gated routes.
        String travelerToken = jwtService.generateToken(999L, "traveler1@example.com");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + travelerToken))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(
                new java.util.LinkedHashMap<>() {{
                    put("email", email);
                    put("password", password);
                }});
        return post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("accessToken").asText();
    }
}
