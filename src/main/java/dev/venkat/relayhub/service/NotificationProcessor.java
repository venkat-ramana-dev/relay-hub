package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final NotificationHistoryService historyService;

    @Value("${relayhub.notification.max-retries:5}")
    private int maxRetries;

    @Transactional
    public List<Notification> fetchAndClaimBatch(int batchSize) {

        List<Notification> batch = notificationRepository.findPendingNotifications(batchSize);

        if (batch.isEmpty()) {
            return batch;
        }

        for (Notification n : batch) {
            NotificationStatus oldStatus = n.getStatus();
            n.setStatus(NotificationStatus.PROCESSING);
            n.setProcessingStartedAt(Instant.now());

            historyService.logHistory(
                    n, oldStatus, NotificationStatus.PROCESSING, "Worker claimed batch", null
            );
        }

        notificationRepository.saveAll(batch);

        return batch;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeDelivery(Long notificationId, DeliveryResult result) {

        Notification notification = notificationRepository.findByIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalStateException("Notification missing during finalize"));

        if (result.success()) {
            handleSuccess(notification, result);
            return;
        }

        if (result.statusCode() != null && result.statusCode() >= 400 && result.statusCode() < 500) {
            markDead(notification, result);
            return;
        }

        if (notification.getRetryCount() >= maxRetries) {
            markDead(notification, result);
            return;
        }

        handleRetry(notification, result);
    }

    private void handleSuccess(Notification notification, DeliveryResult result) {
        NotificationStatus oldStatus = notification.getStatus();
        notification.setStatus(NotificationStatus.SUCCESS);
        notification.setProcessingStartedAt(null);
        notification.setLastFailureReason(null);
        notificationRepository.save(notification);

        historyService.logHistory(
                notification, oldStatus, NotificationStatus.SUCCESS, "Notification delivered successfully", result.statusCode()
        );
        log.info("Notification {} delivered successfully", notification.getId());
    }

    private void markDead(Notification notification, DeliveryResult result) {
        NotificationStatus oldStatus = notification.getStatus();
        notification.setStatus(NotificationStatus.DEAD);
        notification.setProcessingStartedAt(null);
        notification.setLastFailureReason(result.errorMessage());
        notificationRepository.save(notification);

        historyService.logHistory(
                notification, oldStatus, NotificationStatus.DEAD, result.errorMessage(), result.statusCode()
        );
        log.warn("Notification {} moved to DEAD", notification.getId());
    }

    private void handleRetry(Notification notification, DeliveryResult result) {
        NotificationStatus oldStatus = notification.getStatus();

        int currentRetryCount = notification.getRetryCount();
        long backoffMinutes = (long) Math.pow(2, currentRetryCount);

        notification.setStatus(NotificationStatus.RETRYING);
        notification.setProcessingStartedAt(null);
        notification.setRetryCount(currentRetryCount + 1);
        notification.setLastFailureReason(result.errorMessage());

        notification.setNextRetryTime(Instant.now().plus(Duration.ofMinutes(backoffMinutes)));

        notificationRepository.save(notification);

        historyService.logHistory(
                notification, oldStatus, NotificationStatus.RETRYING, result.errorMessage(), result.statusCode()
        );

        log.warn("Notification {} scheduled for retry {} in {} minute(s)",
                notification.getId(), currentRetryCount + 1, backoffMinutes);
    }

    @Transactional
    public int recoverStuckNotifications(Duration processingTimeout, int batchSize) {

        Instant cutoffTime = Instant.now().minus(processingTimeout);

        List<Notification> stuckNotifications =
                notificationRepository.findAndLockStuckProcessingNotifications(cutoffTime, batchSize);

        for (Notification notification : stuckNotifications) {

            NotificationStatus oldStatus = notification.getStatus();

            if (notification.getRetryCount() >= maxRetries) {

                notification.setStatus(NotificationStatus.DEAD);
                notification.setLastFailureReason(
                        "Processing timed out and maximum retries exceeded"
                );

                notification.setProcessingStartedAt(null);

                historyService.logHistory(
                        notification,
                        oldStatus,
                        NotificationStatus.DEAD,
                        "Processing timed out and maximum retries exceeded",
                        null
                );

            } else {

                int currentRetryCount = notification.getRetryCount();
                int newRetryCount = currentRetryCount + 1;

                long backoffMinutes = (long) Math.pow(2, currentRetryCount);

                notification.setStatus(NotificationStatus.RETRYING);
                notification.setRetryCount(newRetryCount);

                notification.setLastFailureReason(
                        "Processing timed out before delivery could be finalized"
                );

                notification.setNextRetryTime(
                        Instant.now().plus(Duration.ofMinutes(backoffMinutes))
                );

                notification.setProcessingStartedAt(null);

                historyService.logHistory(
                        notification,
                        oldStatus,
                        NotificationStatus.RETRYING,
                        "Recovered stuck processing notification",
                        null
                );
            }
        }

        notificationRepository.saveAll(stuckNotifications);

        return stuckNotifications.size();
    }
}