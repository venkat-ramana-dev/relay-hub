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
    public NotificationResponse schedule(ScheduleNotificationRequest request, String userEmail) {

        log.info("Scheduling Notification for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with Email: " + userEmail));

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
