package dev.venkat.relayhub.service;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.NotificationHistory;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationHistoryRepository historyRepository;

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
