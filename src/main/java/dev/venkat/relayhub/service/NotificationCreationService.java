package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import dev.venkat.relayhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationCreationService {

    private final NotificationRepository notificationRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final NotificationHistoryService historyService;

    @Transactional
    public Notification createNotification(
            User user,
            ScheduleNotificationRequest request,
            String idempotencyKey) {

        Notification notification = Notification.builder()
                .user(user)
                .targetUrl(request.targetUrl())
                .payload(request.payload().toString())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledTime(
                        request.scheduledTime() != null
                                ? request.scheduledTime()
                                : Instant.now()
                )
                .build();

        Notification saved = notificationRepository.save(notification);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .keyName(idempotencyKey)
                .user(user)
                .notification(saved)
                .build();

        idempotencyRepository.save(record);

        historyService.logHistory(
                saved,
                null,
                saved.getStatus(),
                "Notification scheduled by client",
                null
        );

        return saved;
    }
}