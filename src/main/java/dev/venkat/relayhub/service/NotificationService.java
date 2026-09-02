package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.exception.IdempotencyConflictException;
import dev.venkat.relayhub.exception.IdempotencyRaceRecoveryException;
import dev.venkat.relayhub.exception.NotificationNotFoundException;
import dev.venkat.relayhub.exception.UserNotFoundException;
import dev.venkat.relayhub.mapper.NotificationMapper;
import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.security.WebhookUrlValidator;
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
    private final IdempotencyRecordRepository idempotencyRepository;
    private final NotificationMapper notificationMapper;
    private final WebhookUrlValidator webhookUrlValidator;
    private final NotificationCreationService notificationCreationService;

    public NotificationResponse schedule(ScheduleNotificationRequest request, String userEmail, String idempotencyKey) {

        webhookUrlValidator.validate(request.targetUrl());

        log.info("Scheduling Notification for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with Email: " + userEmail));

        Optional<IdempotencyRecord> existingRecord = idempotencyRepository.findByKeyNameAndUser(idempotencyKey, user);
        if (existingRecord.isPresent()) {
            log.warn("Idempotency hit! Returning cached notification for key: {}", idempotencyKey);
            return notificationMapper.mapToResponse(existingRecord.get().getNotification());
        }

        try {
            Notification saved =
                    notificationCreationService.createNotification(
                            user,
                            request,
                            idempotencyKey);

            return notificationMapper.mapToResponse(saved);

        } catch (IdempotencyConflictException e) {

            log.warn("Idempotency race detected for key: {}", idempotencyKey);

            IdempotencyRecord raceRecord = idempotencyRepository.findByKeyNameAndUser(idempotencyKey, user)
                            .orElseThrow(() -> new IdempotencyRaceRecoveryException("Failed to recover idempotency race for key: " + idempotencyKey));

            return notificationMapper.mapToResponse(raceRecord.getNotification());
        }
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long notificationId, String userEmail) {

        log.info("Attempting to fetch notification {} of user {}", notificationId, userEmail);

        Notification notification = notificationRepository.findByIdAndUser_Email(notificationId, userEmail)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found or access denied. Notification Id: " + notificationId));

        return notificationMapper.mapToResponse(notification);
    }
}
