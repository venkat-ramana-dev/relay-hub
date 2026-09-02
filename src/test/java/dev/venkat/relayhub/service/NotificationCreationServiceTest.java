package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import dev.venkat.relayhub.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCreationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private NotificationHistoryService historyService;

    @InjectMocks
    private NotificationCreationService notificationCreationService;

    private User testUser;
    private ScheduleNotificationRequest request;

    private final String IDEMPOTENCY_KEY = "idem-key-123";

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode payload = objectMapper.readTree(
                "{\"message\": \"Hello\"}"
        );

        request = new ScheduleNotificationRequest(
                "https://webhook.example.com",
                payload,
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should create notification and idempotency record successfully")
    void createNotification_WhenValidRequest_SavesAllData() {

        // Arrange
        Notification savedNotification = Notification.builder()
                .id(200L)
                .user(testUser)
                .targetUrl(request.targetUrl())
                .payload(request.payload().toString())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledTime(request.scheduledTime())
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(savedNotification);

        // Act
        Notification result =
                notificationCreationService.createNotification(
                        testUser,
                        request,
                        IDEMPOTENCY_KEY
                );

        // Assert
        assertNotNull(result);
        assertEquals(200L, result.getId());

        verify(notificationRepository, times(1))
                .save(any(Notification.class));

        verify(idempotencyRepository, times(1))
                .save(any(IdempotencyRecord.class));

        verify(historyService, times(1))
                .logHistory(
                        eq(savedNotification),
                        isNull(),
                        eq(NotificationStatus.PENDING),
                        eq("Notification scheduled by client"),
                        isNull()
                );
    }

    @Test
    @DisplayName("Should create notification with requested values")
    void createNotification_ShouldPopulateNotificationCorrectly() {

        // Arrange
        Notification savedNotification = Notification.builder()
                .id(200L)
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(savedNotification);

        // Act
        notificationCreationService.createNotification(
                testUser,
                request,
                IDEMPOTENCY_KEY
        );

        // Assert
        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();

        assertEquals(testUser, notification.getUser());
        assertEquals(request.targetUrl(), notification.getTargetUrl());
        assertEquals(
                request.payload().toString(),
                notification.getPayload()
        );
        assertEquals(
                NotificationStatus.PENDING,
                notification.getStatus()
        );
        assertEquals(0, notification.getRetryCount());
        assertEquals(
                request.scheduledTime(),
                notification.getScheduledTime()
        );
    }

    @Test
    @DisplayName("Should create idempotency record linked to saved notification")
    void createNotification_ShouldLinkIdempotencyRecordCorrectly() {

        // Arrange
        Notification savedNotification = Notification.builder()
                .id(200L)
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(savedNotification);

        // Act
        notificationCreationService.createNotification(
                testUser,
                request,
                IDEMPOTENCY_KEY
        );

        // Assert
        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);

        verify(idempotencyRepository)
                .save(captor.capture());

        IdempotencyRecord record = captor.getValue();

        assertEquals(IDEMPOTENCY_KEY, record.getKeyName());
        assertEquals(testUser, record.getUser());
        assertEquals(savedNotification, record.getNotification());
    }

    @Test
    @DisplayName("Should propagate exception when idempotency constraint is violated")
    void createNotification_WhenIdempotencyConstraintFails_PropagatesException() {

        // Arrange
        Notification savedNotification = Notification.builder()
                .id(200L)
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(savedNotification);

        when(idempotencyRepository.save(any(IdempotencyRecord.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Unique constraint violation"
                        )
                );

        // Act & Assert
        assertThrows(
                DataIntegrityViolationException.class,
                () -> notificationCreationService.createNotification(
                        testUser,
                        request,
                        IDEMPOTENCY_KEY
                )
        );

        verify(notificationRepository, times(1))
                .save(any(Notification.class));

        verify(idempotencyRepository, times(1))
                .save(any(IdempotencyRecord.class));
    }
}