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

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "JWT_SECRET=ZHVtbXktc2VjcmV0LWtleS1mb3ItdGVzdGluZy1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc="
})
@DisplayName("E2E-Style API Tests: Security & Data Isolation Flow")
class SecurityIsolationIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE TABLE idempotency_records, notifications, users CASCADE");
    }

    @Test
    @DisplayName("A user cannot access a notification belonging to another user")
    void userCannotReadOtherUsersNotification() throws Exception {

        // ==========================================
        // 1. SETUP USER A (The Owner)
        // ==========================================
        String userAPayload = """
                {
                    "name": "Alice Owner",
                    "email": "alice@relayhub.dev",
                    "password": "passwordA123!"
                }
                """;
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(userAPayload));
        MvcResult loginA = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(userAPayload)).andReturn();
        String jwtA = objectMapper.readTree(loginA.getResponse().getContentAsString()).get("token").asText();

        // ==========================================
        // 2. SETUP USER B (The Attacker)
        // ==========================================
        String userBPayload = """
                {
                    "name": "Bob Attacker",
                    "email": "bob@relayhub.dev",
                    "password": "passwordB123!"
                }
                """;
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(userBPayload));
        MvcResult loginB = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(userBPayload)).andReturn();
        String jwtB = objectMapper.readTree(loginB.getResponse().getContentAsString()).get("token").asText();

        // ==========================================
        // 3. USER A CREATES A NOTIFICATION
        // ==========================================
        String scheduledTime = Instant.now()
                .plus(Duration.ofMinutes(10))
                .toString();

        String notificationPayload = """
                {
                    "targetUrl": "https://webhook.site/alice-secret",
                    "payload": "{\\"secret\\": \\"data\\"}",
                    "scheduledTime": "%s"
                }
                """.formatted(scheduledTime);

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + jwtA)
                        .header("Idempotency-Key", "alice-req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long notificationId = responseNode.get("id").asLong();

        // ==========================================
        // 4. USER B TRIES TO STEAL IT
        // ==========================================
        mockMvc.perform(get("/api/notifications/" + notificationId)
                        .header("Authorization", "Bearer " + jwtB)) // <-- Using Bob's Token!
                .andDo(print())
                .andExpect(status().isNotFound());
    }
}