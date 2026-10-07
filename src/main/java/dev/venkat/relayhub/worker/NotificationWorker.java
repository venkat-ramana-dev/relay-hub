package dev.venkat.relayhub.worker;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.service.DeliveryService;
import dev.venkat.relayhub.service.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {

    private final NotificationProcessor notificationProcessor;
    private final DeliveryService deliveryService;

    @Value("${relayhub.worker.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${relayhub.worker.poll-interval}")
    public void pollNotifications() {

        List<Notification> batch = notificationProcessor.fetchAndClaimBatch(batchSize);

        if (!batch.isEmpty()) {
            log.info("Worker picked up {} notifications for processing", batch.size());
        }

        for (Notification notification : batch) {
            try {
                DeliveryResult result = deliveryService.deliver(notification);
                notificationProcessor.finalizeDelivery(notification.getId(), result);
                } catch (Exception e) {
                log.error("Unhandled exception processing notification ID: {}", notification.getId(), e);
            }
        }
    }

}