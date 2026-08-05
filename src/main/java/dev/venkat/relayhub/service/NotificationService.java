package dev.venkat.relayhub.service;


import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.NotificationRepository;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationHistoryService historyService;

    @Transactional
    public NotificationResponse schedule(ScheduleNotificationRequest request) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.userId()));

        Notification notification = Notification.builder()
                .user(user)
                .targetUrl(request.targetUrl())
                .payload(request.payload().toString())
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .scheduledTime(request.scheduledTime() != null ? request.scheduledTime() : LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);

        historyService.logHistory(saved, null, "Notification scheduled by client", null);

        return mapToResponse(saved);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTargetUrl(),
                notification.getStatus().name(),
                notification.getRetryCount(),
                notification.getScheduledTime(),
                notification.getLastFailureReason()
        );
    }
}
