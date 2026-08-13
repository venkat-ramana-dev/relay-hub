package dev.venkat.relayhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "JWT_SECRET=dGhpcy1pcy1hLWR1bW15LXNlY3JldC1rZXktZm9yLXRlc3Rpbmc="
})
@DisplayName("E2E-Style API Tests: Authentication Flow")
class AuthenticationIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE");
    }

    @Test
    @DisplayName("Full User Journey: Unauthenticated -> Register -> Login -> Authenticated")
    void fullAuthenticationFlow() throws Exception {

        // 1. Try to access a protected route without a token
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden()); // Or isUnauthorized() depending on your SecurityConfig

        // 2. Register a new user
        String registerPayload = """
                {
                    "name": "E2E Test User",
                    "email": "e2e@relayhub.dev",
                    "password": "strongPassword123!"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        // 3. Login with the exact same credentials
        String loginPayload = """
                {
                    "email": "e2e@relayhub.dev",
                    "password": "strongPassword123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        // 4. Extract and strictly validate the JWT from the JSON response
        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        assertThat(jsonNode.hasNonNull("token")).isTrue();

        String jwtToken = jsonNode.get("token").asText();
        assertThat(jwtToken).isNotBlank();

        // 5. Use the JWT to successfully create a notification
        String notificationPayload = """
                {
                    "targetUrl": "https://webhook.site/test",
                    "payload": "{\\"test\\": \\"data\\"}",
                    "scheduledTime": "2026-08-14T10:00:00"
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("Idempotency-Key", "test-req-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload))
                .andExpect(status().isCreated());
    }
}