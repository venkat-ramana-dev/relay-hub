package dev.venkat.relayhub.worker;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.service.DeliveryService;
import dev.venkat.relayhub.service.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;
    private final DeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${relayhub.worker.poll-interval}")
    public void pollNotifications() {

        List<Notification> batch = notificationRepository.findPendingNotifications();

        if (!batch.isEmpty()) {
            log.info("Worker picked up {} notifications for processing", batch.size());
        }

        for (Notification notification : batch) {
            try {
                boolean claimed = notificationProcessor.markProcessing(notification);
                if (claimed) {
                    DeliveryResult result = deliveryService.deliver(notification);
                    notificationProcessor.finalizeDelivery(notification, result);
                }
            } catch (Exception e) {
                log.error("Unhandled exception processing notification ID: {}", notification.getId(), e);
            }
        }
    }

}