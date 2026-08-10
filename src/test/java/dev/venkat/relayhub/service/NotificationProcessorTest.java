package dev.venkat.relayhub.service;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationHistoryService historyService;

    @InjectMocks
    private NotificationProcessor notificationProcessor;

    private Notification requestNotification;

    @BeforeEach
    void setUp() {
        requestNotification = Notification.builder()
                .id(100L)
                .build();
    }

    @Test
    @DisplayName("Should return false when notification is missing in database")
    void markProcessing_WhenNotificationNotFound_ReturnsFalse() {
        // Arrange
        when(notificationRepository.findById(100L)).thenReturn(Optional.empty());

        // Act
        boolean result = notificationProcessor.markProcessing(requestNotification);

        // Assert
        assertFalse(result);
        verify(notificationRepository, never()).save(any());
        verify(historyService, never()).logHistory(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return false when notification status is invalid (e.g., SUCCESS)")
    void markProcessing_WhenStatusIsInvalid_ReturnsFalse() {
        // Arrange
        Notification dbNotification = Notification.builder()
                .id(100L)
                .status(NotificationStatus.SUCCESS)
                .build();

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

        // Act
        boolean result = notificationProcessor.markProcessing(requestNotification);

        // Assert
        assertFalse(result);
        verify(notificationRepository, never()).save(any());
        verify(historyService, never()).logHistory(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should return true, update status to PROCESSING, save and log history when status is PENDING")
    void markProcessing_WhenStatusIsPending_ReturnsTrueAndUpdatesStatus() {
        // Arrange
        Notification dbNotification = Notification.builder()
                .id(100L)
                .status(NotificationStatus.PENDING)
                .build();

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

        // Act
        boolean result = notificationProcessor.markProcessing(requestNotification);

        // Assert
        assertTrue(result);
        assertEquals(NotificationStatus.PROCESSING, dbNotification.getStatus());

        verify(notificationRepository, times(1)).save(dbNotification);
        verify(historyService, times(1)).logHistory(
                eq(dbNotification),
                eq(NotificationStatus.PENDING),
                eq(NotificationStatus.PROCESSING),
                eq("Worker claimed notification"),
                isNull()
        );
    }

    @Test
    @DisplayName("Should return true, update status to PROCESSING, save and log history when status is RETRYING")
    void markProcessing_WhenStatusIsRetrying_ReturnsTrueAndUpdatesStatus() {
        // Arrange
        Notification dbNotification = Notification.builder()
                .id(100L)
                .status(NotificationStatus.RETRYING)
                .build();

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

        // Act
        boolean result = notificationProcessor.markProcessing(requestNotification);

        // Assert
        assertTrue(result);
        assertEquals(NotificationStatus.PROCESSING, dbNotification.getStatus());

        verify(notificationRepository, times(1)).save(dbNotification);
        verify(historyService, times(1)).logHistory(
                eq(dbNotification),
                eq(NotificationStatus.RETRYING),
                eq(NotificationStatus.PROCESSING),
                eq("Worker claimed notification"),
                isNull()
        );
    }
}
