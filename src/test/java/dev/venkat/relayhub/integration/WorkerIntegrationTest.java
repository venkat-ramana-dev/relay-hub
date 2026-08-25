package dev.venkat.relayhub.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.worker.NotificationWorker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "JWT_SECRET=ZHVtbXktc2VjcmV0LWtleS1mb3ItdGVzdGluZy1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzLWxvbmc=",
        "relayhub.worker.poll-interval=9999999"
})
@DisplayName("Integration Test: Notification Worker")
class WorkerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    private static WireMockServer wireMockServer;

    @Autowired
    private NotificationWorker notificationWorker;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.resetAll();
        }

        jdbcTemplate.execute("TRUNCATE TABLE notification_history, notifications, users, idempotency_records CASCADE");
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName(
            "Worker sends HTTP request and marks notification as SUCCESS"
    )
    void workerSendsHttpRequestAndMarksNotificationSuccess() {

        // ============================================================
        // 1. ARRANGE: Configure the external webhook server
        // ============================================================

        wireMockServer.stubFor(
                post(urlEqualTo("/webhook"))
                        .withHeader(
                                "Content-Type",
                                containing("application/json")
                        )
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                        )
        );

        String webhookUrl =
                "http://localhost:"
                        + wireMockServer.port()
                        + "/webhook";


        // ============================================================
        // 2. ARRANGE: Create test user
        // ============================================================

        User user = new User();

        user.setName("Worker Test");
        user.setEmail("worker@relayhub.dev");
        user.setPassword("hashedPass");
        user.setRole(Role.USER);

        user = userRepository.save(user);


        // ============================================================
        // 3. ARRANGE: Create eligible PENDING notification
        // ============================================================

        Notification notification = new Notification();

        notification.setUser(user);
        notification.setTargetUrl(webhookUrl);
        notification.setPayload("""
                {
                    "event": "test",
                    "message": "Hello from RelayHub"
                }
                """);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setScheduledTime(
                Instant.now().minus(Duration.ofMinutes(10))
        );

        notification = notificationRepository.save(notification);

        Long notificationId = notification.getId();


        // ============================================================
        // 4. ACT: Trigger the REAL worker
        // ============================================================

        notificationWorker.pollNotifications();


        // ============================================================
        // 5. ASSERT: Verify that a REAL HTTP request was received
        // ============================================================

        wireMockServer.verify(
                1,
                postRequestedFor(urlEqualTo("/webhook"))
                        .withHeader(
                                "Content-Type",
                                containing("application/json")
                        )
                        .withRequestBody(
                                containing("\"event\"")
                        )
                        .withRequestBody(
                                containing("\"message\"")
                        )
        );


        // ============================================================
        // 6. ASSERT: Verify database state
        // ============================================================

        Notification processed =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow();

        assertThat(processed.getStatus())
                .isEqualTo(NotificationStatus.SUCCESS);

        assertThat(processed.getRetryCount())
                .isEqualTo(0);
    }
}