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

import java.util.Collections;
import java.util.List;
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
    @DisplayName("fetchAndClaimBatch() Tests")
    class FetchAndClaimBatchTests {

        @Test
        @DisplayName("Should return empty list when no pending notifications exist")
        void fetchAndClaimBatch_WhenEmpty_ReturnsEmptyList() {
            // Arrange
            int batchSize = 10;
            when(notificationRepository.findPendingNotifications(batchSize)).thenReturn(Collections.emptyList());

            // Act
            List<Notification> result = notificationProcessor.fetchAndClaimBatch(batchSize);

            // Assert
            assertTrue(result.isEmpty());

            verify(notificationRepository, times(1)).findPendingNotifications(batchSize);
            verify(historyService, never()).logHistory(any(), any(), any(), any(), any());
            verify(notificationRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Should update status to PROCESSING, log history, and explicitly saveAll")
        void fetchAndClaimBatch_WhenNotificationsExist_ClaimsAndReturns() {
            // Arrange
            int batchSize = 10;
            Notification dbNotification = Notification.builder()
                    .id(100L)
                    .status(NotificationStatus.PENDING)
                    .build();

            List<Notification> mockBatch = List.of(dbNotification);

            when(notificationRepository.findPendingNotifications(batchSize)).thenReturn(mockBatch);
            when(notificationRepository.saveAll(mockBatch)).thenReturn(mockBatch);

            // Act
            List<Notification> result = notificationProcessor.fetchAndClaimBatch(batchSize);

            // Assert
            assertEquals(1, result.size());
            assertEquals(NotificationStatus.PROCESSING, result.get(0).getStatus());

            verify(notificationRepository, times(1))
                    .findPendingNotifications(batchSize);

            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    eq(NotificationStatus.PENDING),
                    eq(NotificationStatus.PROCESSING),
                    eq("Worker claimed batch"),
                    isNull()
            );

            verify(notificationRepository, times(1)).saveAll(mockBatch);
        }

        @Test
        @DisplayName("Should successfully claim a mixed batch of PENDING and RETRYING notifications")
        void fetchAndClaimBatch_WhenMixedStatuses_ClaimsAll() {
            // Arrange
            int batchSize = 10;

            Notification pendingDbNotification = Notification.builder()
                    .id(101L).status(NotificationStatus.PENDING).build();

            Notification retryingDbNotification = Notification.builder()
                    .id(102L).status(NotificationStatus.RETRYING).build();

            List<Notification> mockBatch = List.of(pendingDbNotification, retryingDbNotification);

            when(notificationRepository.findPendingNotifications(batchSize)).thenReturn(mockBatch);
            when(notificationRepository.saveAll(mockBatch)).thenReturn(mockBatch);

            // Act
            List<Notification> result = notificationProcessor.fetchAndClaimBatch(batchSize);

            // Assert
            assertEquals(2, result.size());
            assertEquals(NotificationStatus.PROCESSING, result.get(0).getStatus());
            assertEquals(NotificationStatus.PROCESSING, result.get(1).getStatus());

            verify(notificationRepository, times(1))
                    .findPendingNotifications(batchSize);

            verify(historyService, times(1)).logHistory(
                    eq(pendingDbNotification),
                    eq(NotificationStatus.PENDING),
                    eq(NotificationStatus.PROCESSING),
                    eq("Worker claimed batch"),
                    isNull()
            );

            verify(historyService, times(1)).logHistory(
                    eq(retryingDbNotification),
                    eq(NotificationStatus.RETRYING),
                    eq(NotificationStatus.PROCESSING),
                    eq("Worker claimed batch"),
                    isNull()
            );

            verify(notificationRepository, times(1))
                    .saveAll(mockBatch);
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

            when(notificationRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    notificationProcessor.finalizeDelivery(requestNotification.getId(), dummyResult)
            );

            assertEquals("Notification missing during finalize", exception.getMessage());
            verify(notificationRepository, never()).save(any());

            verify(historyService, never()).logHistory(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should process success when webhook returns 200 OK")
        void finalizeDelivery_WhenResultIsSuccess_ProcessesSuccess() {
            // Arrange
            DeliveryResult result = DeliveryResult.success(200);
            when(notificationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification.getId(), result);

            // Assert Side Effects of handleSuccess()
            assertEquals(NotificationStatus.SUCCESS, dbNotification.getStatus());

            verify(notificationRepository, times(1)).save(dbNotification);

            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    eq(NotificationStatus.PROCESSING),
                    eq(NotificationStatus.SUCCESS),
                    eq("Notification delivered successfully"),
                    eq(200)
            );
        }

        @Test
        @DisplayName("Should mark dead immediately on HTTP 4xx Client Errors")
        void finalizeDelivery_WhenResultIs4xxClientError_MarksDead() {
            // Arrange
            DeliveryResult result = DeliveryResult.failure(404, "Not Found");
            when(notificationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification.getId(), result);

            // Assert Side Effects of markDead()
            assertEquals(NotificationStatus.DEAD, dbNotification.getStatus());

            verify(notificationRepository, times(1)).save(dbNotification);

            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    eq(NotificationStatus.PROCESSING),
                    eq(NotificationStatus.DEAD),
                    eq("Not Found"),
                    eq(404)
            );
        }

        @Test
        @DisplayName("Should mark dead when retry count reaches maxRetries")
        void finalizeDelivery_WhenMaxRetriesReached_MarksDead() {
            // Arrange
            dbNotification.setRetryCount(5); // Max retries reached
            DeliveryResult result = DeliveryResult.failure(500, "Internal Server Error");

            when(notificationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification.getId(), result);

            // Assert Side Effects of markDead()
            assertEquals(NotificationStatus.DEAD, dbNotification.getStatus());

            verify(notificationRepository, times(1)).save(dbNotification);

            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    eq(NotificationStatus.PROCESSING),
                    eq(NotificationStatus.DEAD),
                    eq("Internal Server Error"),
                    eq(500)
            );
        }

        @Test
        @DisplayName("Should handle retry logic and increment count when eligible")
        void finalizeDelivery_WhenEligibleForRetry_HandlesRetry() {
            // Arrange
            dbNotification.setRetryCount(2); // Well below maxRetries of 5
            DeliveryResult result = DeliveryResult.failure(500, "Internal Server Error");

            when(notificationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(dbNotification));

            // Act
            notificationProcessor.finalizeDelivery(dbNotification.getId(), result);

            // Assert Side Effects of handleRetry()
            assertEquals(NotificationStatus.RETRYING, dbNotification.getStatus());
            assertEquals(3, dbNotification.getRetryCount()); // Verifies count incremented
            assertNotNull(dbNotification.getNextRetryTime());  // Verifies exponential backoff ran

            verify(notificationRepository, times(1)).save(dbNotification);

            verify(historyService, times(1)).logHistory(
                    eq(dbNotification),
                    eq(NotificationStatus.PROCESSING),
                    eq(NotificationStatus.RETRYING),
                    eq("Internal Server Error"),
                    eq(500)
            );
        }
    }


}
