package dev.venkat.relayhub.service;


import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.exception.NotificationNotFoundException;
import dev.venkat.relayhub.exception.UserNotFoundException;
import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationHistoryService historyService;
    private final IdempotencyRecordRepository idempotencyRepository;

    @Transactional
    public NotificationResponse schedule(ScheduleNotificationRequest request, String userEmail, String idempotencyKey) {

        log.info("Scheduling Notification for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with Email: " + userEmail));

        Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findByKeyNameAndUser(idempotencyKey, user);
        if (existingRecord.isPresent()) {
            log.warn("Idempotency hit! Returning cached notification for key: {}", idempotencyKey);
            return mapToResponse(existingRecord.get().getNotification());
        }

        Notification notification = Notification.builder()
                .user(user)
                .targetUrl(request.targetUrl())
                .payload(request.payload().toString())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledTime(request.scheduledTime() != null ? request.scheduledTime() : Instant.now())
                .build();

        try {
            Notification saved = notificationRepository.save(notification);

            IdempotencyRecord record = IdempotencyRecord.builder()
                    .keyName(idempotencyKey)
                    .user(user)
                    .notification(saved)
                    .build();
            idempotencyRepository.save(record);

            historyService.logHistory(saved, null, saved.getStatus(), "Notification scheduled by client", null);

            return mapToResponse(saved);

        } catch (DataIntegrityViolationException e) {

            log.warn("Race condition caught for key: {}. Fetching the successfully saved record.", idempotencyKey);

            IdempotencyRecord raceRecord = idempotencyRepository.findByKeyNameAndUser(idempotencyKey, user)
                    .orElseThrow(() -> new RuntimeException("Critical failure recovering idempotency key"));

            return mapToResponse(raceRecord.getNotification());
        }
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long notificationId, String userEmail) {

        log.info("Attempting to fetch notification {} of user {}", notificationId, userEmail);

        Notification notification = notificationRepository.findByIdAndUser_Email(notificationId, userEmail)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found or access denied. Notification Id: " + notificationId));

        return mapToResponse(notification);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTargetUrl(),
                notification.getPayload(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getScheduledTime(),
                notification.getNextRetryTime(),
                notification.getLastFailureReason(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}
