package dev.venkat.relayhub.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "JWT_SECRET=ZHVtbXktc2VjcmV0LWtleS1mb3ItdGVzdGluZy1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc="
})
@DisplayName("E2E-Style API Tests: Idempotency Flow")
class IdempotencyIntegrationTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        // Clean up everything so tests don't pollute each other
        jdbcTemplate.execute("TRUNCATE TABLE idempotency_records, notifications, users CASCADE");
    }

    @Test
    @DisplayName("Duplicate POST requests with the same Idempotency-Key should only create ONE record")
    void testIdempotencyPreventsDuplicateNotifications() throws Exception {

        // 1. Quick Setup: Register & Login to get a valid JWT
        String authPayload = """
                {
                    "name": "Idempotency Tester",
                    "email": "idempotency@relayhub.dev",
                    "password": "securePass123!"
                }
                """;

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authPayload)).andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(authPayload)).andReturn();

        String jwtToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        // 2. Define the Notification Payload and Key
        String idempotencyKey = "req-uuid-9999-8888";
        String notificationPayload = """
                {
                    "targetUrl": "https://webhook.site/idempotent",
                    "payload": "{\\"event\\": \\"payment_success\\"}",
                    "scheduledTime": "2026-08-15T10:00:00"
                }
                """;

        // 3. FIRST REQUEST: Should succeed and create the notification
        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload))
                .andExpect(status().isCreated());

        // 4. SECOND REQUEST: The exact same request (simulating a client retry)
        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload))
                // Note: We use is2xxSuccessful() because some APIs return 200 OK for cached responses instead of 201
                .andExpect(status().is2xxSuccessful());

        // 5. THE ULTIMATE ASSERTION: Check the database directly!
        // Even though we hit the API twice, the database must only have ONE notification.
        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class);

        Integer idempotencyRecordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records", Integer.class);

        assertThat(notificationCount).isEqualTo(1);
        assertThat(idempotencyRecordCount).isEqualTo(1);
    }
}