package dev.venkat.relayhub.worker;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.service.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    @Scheduled(fixedRateString = "${relayhub.worker.poll-interval}")
    public void pollNotifications() {

        List<Notification> notifications = fetchLockedBatch();

        if (notifications.isEmpty()) {
            return;
        }

        log.info("Worker found {} notification(s) to process.",
                notifications.size());

        for (Notification notification : notifications) {
            try {
                notificationProcessor.process(notification);
            } catch (Exception ex) {
                log.error("Failed processing notification {}",
                        notification.getId(), ex);
            }
        }

    }

    @Transactional
    protected List<Notification> fetchLockedBatch() {
        return notificationRepository.findPendingNotifications();
    }

}
