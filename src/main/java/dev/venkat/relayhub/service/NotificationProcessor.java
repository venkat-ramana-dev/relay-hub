package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final NotificationHistoryService historyService;

    private static final int MAX_RETRIES = 5;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessing(Notification notification) {

        Notification n = notificationRepository.findById(notification.getId()).orElse(null);
        if (n == null) {
            return false;
        }

        if (n.getStatus() != NotificationStatus.PENDING && n.getStatus() != NotificationStatus.RETRYING) {
            return false;
        }

        NotificationStatus oldStatus = n.getStatus();
        n.setStatus(NotificationStatus.PROCESSING);
        notificationRepository.save(n);

        historyService.logHistory(
                n, oldStatus, NotificationStatus.PROCESSING, "Worker claimed notification", null
        );
        log.info("Notification {} moved to PROCESSING", n.getId());

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeDelivery(Notification notification, DeliveryResult result) {

        if (result.success()) {
            handleSuccess(notification, result);
            return;
        }

        if (result.statusCode() != null && result.statusCode() >= 400 && result.statusCode() < 500) {
            markDead(notification, result);
            return;
        }

        if (notification.getRetryCount() >= MAX_RETRIES) {
            markDead(notification, result);
            return;
        }

        handleRetry(notification, result);
    }

    private void handleSuccess(Notification notification, DeliveryResult result) {
        NotificationStatus oldStatus = notification.getStatus();
        notification.setStatus(NotificationStatus.SUCCESS);
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
        notification.setLastFailureReason(result.errorMessage());
        notificationRepository.save(notification);

        historyService.logHistory(
                notification, oldStatus, NotificationStatus.DEAD, result.errorMessage(), result.statusCode()
        );
        log.warn("Notification {} moved to DEAD", notification.getId());
    }

    private void handleRetry(Notification notification, DeliveryResult result) {
        NotificationStatus oldStatus = notification.getStatus();
        notification.setStatus(NotificationStatus.RETRYING);
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setLastFailureReason(result.errorMessage());

        notification.setNextRetryTime(LocalDateTime.now().plusMinutes(1));

        notificationRepository.save(notification);

        historyService.logHistory(
                notification, oldStatus, NotificationStatus.RETRYING, result.errorMessage(), result.statusCode()
        );
        log.warn("Notification {} scheduled for retry", notification.getId());
    }
}