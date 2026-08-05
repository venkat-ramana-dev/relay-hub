package dev.venkat.relayhub.service;


import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.exception.NotificationNotFoundException;
import dev.venkat.relayhub.exception.UserNotFoundException;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationHistoryService historyService;

    @Transactional
    public NotificationResponse schedule(ScheduleNotificationRequest request) {

        log.info("Scheduling Notification for userId: {} targetUrl: {}", request.userId(), request.targetUrl());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + request.userId()));

        Notification notification = Notification.builder()
                .user(user)
                .targetUrl(request.targetUrl())
                .payload(request.payload().toString())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledTime(request.scheduledTime() != null ? request.scheduledTime() : LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);

        historyService.logHistory(saved, null, saved.getStatus(),"Notification scheduled by client", null);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long notificationId, Long userId) {

        log.info("Attempting to fetch notification {} for user {}", notificationId, userId);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found or access denied. Notification Id: " + notificationId + " User Id: " + userId));

        return mapToResponse(notification);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTargetUrl(),
                notification.getStatus(),
                notification.getRetryCount(),
                notification.getScheduledTime(),
                notification.getLastFailureReason()
        );
    }
}
