package dev.venkat.relayhub.service;


import dev.venkat.relayhub.exception.NotificationNotFoundException;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.exception.UnsafeWebhookUrlException;
import dev.venkat.relayhub.exception.UserNotFoundException;
import dev.venkat.relayhub.mapper.NotificationMapper;
import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.security.WebhookUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private NotificationCreationService notificationCreationService;

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private WebhookUrlValidator webhookUrlValidator;

    @Nested
    @DisplayName("schedule() Tests")
    class ScheduleTests {

        private User testUser;
        private ScheduleNotificationRequest request;
        private final String USER_EMAIL = "test@example.com";
        private final String IDEMPOTENCY_KEY = "idem-key-123";

        @BeforeEach
        void setUp() throws Exception { // <-- Add throws Exception here
            testUser = User.builder().id(1L).email(USER_EMAIL).build();

            // 1. Create an ObjectMapper
            ObjectMapper objectMapper= new ObjectMapper();

            // 2. Parse the string into a JsonNode
            JsonNode dummyPayload = objectMapper.readTree("{\"message\": \"Hello\"}");

            // 3. Pass the JsonNode into your request
            request = new ScheduleNotificationRequest(
                    "https://webhook.example.com",
                    dummyPayload,
                    Instant.now()
            );
        }

        @Test
        @DisplayName("Should throw exception when user email is not found")
        void schedule_WhenUserNotFound_ThrowsException() {
            // Arrange
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
                    notificationService.schedule(request, USER_EMAIL, IDEMPOTENCY_KEY)
            );

            assertTrue(exception.getMessage().contains(USER_EMAIL));

            // Verify downstream dependencies were untouched
            verify(idempotencyRepository, never()).findByKeyNameAndUser(any(), any());
            verify(notificationCreationService, never()).createNotification(any(), any(), any());
        }

        @Test
        @DisplayName("Should return cached response immediately when idempotency key exists")
        void schedule_WhenIdempotencyKeyExists_ReturnsCachedResponse() {
            // Arrange
            Notification cachedNotification = Notification.builder().id(100L).status(NotificationStatus.PENDING).build();
            IdempotencyRecord existingRecord = IdempotencyRecord.builder()
                    .keyName(IDEMPOTENCY_KEY)
                    .notification(cachedNotification)
                    .build();

            NotificationResponse dummyResponse = new NotificationResponse(100L, null, null, null, null, null, null, null, null, null);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
            when(idempotencyRepository.findByKeyNameAndUser(IDEMPOTENCY_KEY, testUser))
                    .thenReturn(Optional.of(existingRecord));

            when(notificationMapper.mapToResponse(cachedNotification)).thenReturn(dummyResponse);

            // Act
            NotificationResponse response = notificationService.schedule(request, USER_EMAIL, IDEMPOTENCY_KEY);

            // Assert
            assertNotNull(response);
            assertEquals(100L, response.id()); // Assuming response maps the ID

            verify(notificationCreationService, never()).createNotification(any(), any(), any());
        }

        @Test
        @DisplayName("Should create and return new notification for a fresh idempotency key")
        void schedule_WhenNewRequest_CreatesAndReturnsResponse() {
            // Arrange
            Notification savedNotification = Notification.builder()
                    .id(200L)
                    .status(NotificationStatus.PENDING)
                    .build();

            NotificationResponse dummyResponse = new NotificationResponse(
                    200L, null, null, null, null,
                    null, null, null, null, null
            );

            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(testUser));

            when(idempotencyRepository.findByKeyNameAndUser(
                    IDEMPOTENCY_KEY, testUser))
                    .thenReturn(Optional.empty());

            when(notificationCreationService.createNotification(
                    testUser,
                    request,
                    IDEMPOTENCY_KEY
            )).thenReturn(savedNotification);

            when(notificationMapper.mapToResponse(savedNotification))
                    .thenReturn(dummyResponse);

            // Act
            NotificationResponse response = notificationService.schedule(
                    request,
                    USER_EMAIL,
                    IDEMPOTENCY_KEY
            );

            // Assert
            assertNotNull(response);
            assertEquals(200L, response.id());

            verify(notificationCreationService, times(1))
                    .createNotification(
                            testUser,
                            request,
                            IDEMPOTENCY_KEY
                    );
        }

        @Test
        @DisplayName("Should return winning notification when idempotency race occurs")
        void schedule_WhenRaceConditionOccurs_ReturnsWinningNotification() {
            // Arrange
            Notification raceWonNotification = Notification.builder()
                    .id(300L)
                    .status(NotificationStatus.PENDING)
                    .build();

            NotificationResponse dummyResponse = new NotificationResponse(
                    300L, null, null, NotificationStatus.PENDING,
                    0, null, null, null, null, null
            );

            IdempotencyRecord raceWonRecord = IdempotencyRecord.builder()
                    .keyName(IDEMPOTENCY_KEY)
                    .user(testUser)
                    .notification(raceWonNotification)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(testUser));

            when(idempotencyRepository.findByKeyNameAndUser(
                    IDEMPOTENCY_KEY, testUser))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(raceWonRecord));

            when(notificationCreationService.createNotification(
                    testUser,
                    request,
                    IDEMPOTENCY_KEY
            )).thenThrow(
                    new DataIntegrityViolationException(
                            "Unique constraint violation"
                    )
            );

            when(notificationMapper.mapToResponse(raceWonNotification))
                    .thenReturn(dummyResponse);

            // Act
            NotificationResponse response = notificationService.schedule(
                    request,
                    USER_EMAIL,
                    IDEMPOTENCY_KEY
            );

            // Assert
            assertNotNull(response);
            assertEquals(300L, response.id());

            verify(notificationCreationService, times(1))
                    .createNotification(
                            testUser,
                            request,
                            IDEMPOTENCY_KEY
                    );

            verify(idempotencyRepository, times(2))
                    .findByKeyNameAndUser(
                            IDEMPOTENCY_KEY,
                            testUser
                    );
        }

        @Test
        @DisplayName("Should reject unsafe webhook URL")
        void shouldRejectUnsafeWebhookUrl() throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode dummyPayload =
                    objectMapper.readTree("{\"message\": \"Hello\"}");

            ScheduleNotificationRequest request = new ScheduleNotificationRequest(
                    "http://127.0.0.1:8080/internal",
                    dummyPayload,
                    Instant.now()
            );

            doThrow(new UnsafeWebhookUrlException("Target URL resolves to a restricted network address"))
                    .when(webhookUrlValidator)
                    .validate(request.targetUrl());

            assertThrows(
                    UnsafeWebhookUrlException.class,
                    () -> notificationService.schedule(
                            request,
                            USER_EMAIL,
                            IDEMPOTENCY_KEY
                    )
            );

            verify(webhookUrlValidator).validate(request.targetUrl());
            verify(userRepository, never()).findByEmail(anyString());
            verify(idempotencyRepository, never()).findByKeyNameAndUser(any(), any());
            verify(notificationCreationService, never()).createNotification(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getNotification() Tests")
    class GetNotificationTests {

        private final Long NOTIFICATION_ID = 100L;
        private final String USER_EMAIL = "test@example.com";
        private Notification dbNotification;

        @BeforeEach
        void setUp() {
            // Setup a dummy notification that the database would return
            dbNotification = Notification.builder()
                    .id(NOTIFICATION_ID)
                    .targetUrl("https://webhook.example.com")
                    .status(NotificationStatus.PENDING)
                    .build();
        }

        @Test
        @DisplayName("Should throw exception when notification doesn't exist or doesn't belong to user")
        void getNotification_WhenNotFoundOrAccessDenied_ThrowsException() {
            // Arrange
            when(notificationRepository.findByIdAndUser_Email(NOTIFICATION_ID, USER_EMAIL))
                    .thenReturn(Optional.empty());

            // Act & Assert
            NotificationNotFoundException exception = assertThrows(NotificationNotFoundException.class, () ->
                    notificationService.getNotification(NOTIFICATION_ID, USER_EMAIL)
            );

            assertTrue(exception.getMessage().contains(String.valueOf(NOTIFICATION_ID)));
        }

        @Test
        @DisplayName("Should return mapped response when notification is found")
        void getNotification_WhenFound_ReturnsResponse() {
            NotificationResponse dummyResponse = new NotificationResponse(
                    NOTIFICATION_ID, "https://webhook.example.com", null, NotificationStatus.PENDING, 0, null, null, null, null, null
            );

            // Arrange
            when(notificationRepository.findByIdAndUser_Email(NOTIFICATION_ID, USER_EMAIL))
                    .thenReturn(Optional.of(dbNotification));

            when(notificationMapper.mapToResponse(dbNotification)).thenReturn(dummyResponse);

            // Act
            NotificationResponse response = notificationService.getNotification(NOTIFICATION_ID, USER_EMAIL);

            // Assert
            assertNotNull(response);
            assertEquals(NOTIFICATION_ID, response.id());
            assertEquals("https://webhook.example.com", response.targetUrl());
            assertEquals(NotificationStatus.PENDING, response.status());
        }
    }
}
