package dev.venkat.relayhub.service;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.NotificationHistory;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationHistoryRepository historyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logHistory(Notification notification, NotificationStatus oldStatus, NotificationStatus newStatus, String message, Integer responseCode) {
        NotificationHistory history = NotificationHistory.builder()
                .notification(notification)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .message(message)
                .responseCode(responseCode)
                .build();

        historyRepository.save(history);
    }
}
