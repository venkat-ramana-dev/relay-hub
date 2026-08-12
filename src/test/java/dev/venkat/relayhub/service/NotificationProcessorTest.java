package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Nested
    @DisplayName("markProcessing() Tests")
    class MarkProcessingTests {

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

    @Nested
    @DisplayName("finalizeDelivery() Tests")
    class FinalizeDeliveryTests {

        private Notification dbNotification;

        @BeforeEach
        void setUp() {
            // Set the @Value maxRetries to 5 for all tests in this block
            ReflectionTestUtils.setField(notificationProcessor, "maxRetries", 5);

            dbNotification = Notification.builder()
                    .id(100L)
                    .status(NotificationStatus.PROCESSING)
                    .retryCount(0)
                    .build();
        }

        @Test
        @DisplayName("Should throw exception when notification is missing in database")
        void finalizeDelivery_WhenNotificationMissing_ThrowsIllegalStateException() {
            // Arrange
            Notification requestNotification = Notification.builder().id(100L).build();
            DeliveryResult dummyResult = DeliveryResult.success(200);

            when(notificationRepository.findById(100L)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    notificationProcessor.finalizeDelivery(requestNotification, dummyResult)
            );

            assertEquals("Notification missing during finalize", exception.getMessage());
            verify(notificationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should process success when webhook returns 200 OK")
        void finalizeDelivery_WhenResultIsSuccess_ProcessesSuccess() {
            // Arrange
            DeliveryResult result = DeliveryResult.success(200);
            when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification, result);

            // Assert Side Effects of handleSuccess()
            assertEquals(NotificationStatus.SUCCESS, dbNotification.getStatus());
            verify(notificationRepository, times(1)).save(dbNotification);
            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    any(),
                    eq(NotificationStatus.SUCCESS),
                    anyString(),
                    anyInt()
            );
        }

        @Test
        @DisplayName("Should mark dead immediately on HTTP 4xx Client Errors")
        void finalizeDelivery_WhenResultIs4xxClientError_MarksDead() {
            // Arrange
            DeliveryResult result = DeliveryResult.failure(404, "Not Found");
            when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification, result);

            // Assert Side Effects of markDead()
            assertEquals(NotificationStatus.DEAD, dbNotification.getStatus());
            verify(notificationRepository, times(1)).save(dbNotification);
            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    any(),
                    eq(NotificationStatus.DEAD),
                    anyString(),
                    anyInt()
            );
        }

        @Test
        @DisplayName("Should mark dead when retry count reaches maxRetries")
        void finalizeDelivery_WhenMaxRetriesReached_MarksDead() {
            // Arrange
            dbNotification.setRetryCount(5); // Max retries reached
            DeliveryResult result = DeliveryResult.failure(500, "Internal Server Error");

            when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification, result);

            // Assert Side Effects of markDead()
            assertEquals(NotificationStatus.DEAD, dbNotification.getStatus());
            verify(notificationRepository, times(1)).save(dbNotification);
        }

        @Test
        @DisplayName("Should handle retry logic and increment count when eligible")
        void finalizeDelivery_WhenEligibleForRetry_HandlesRetry() {
            // Arrange
            dbNotification.setRetryCount(2); // Well below maxRetries of 5
            DeliveryResult result = DeliveryResult.failure(500, "Internal Server Error");

            when(notificationRepository.findById(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification, result);

            // Assert Side Effects of handleRetry()
            assertEquals(NotificationStatus.RETRYING, dbNotification.getStatus());
            assertEquals(3, dbNotification.getRetryCount()); // Verifies count incremented
            assertNotNull(dbNotification.getNextRetryTime());  // Verifies exponential backoff ran

            verify(notificationRepository, times(1)).save(dbNotification);
            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    any(),
                    eq(NotificationStatus.RETRYING),
                    anyString(),
                    anyInt()
            );
        }
    }


}
