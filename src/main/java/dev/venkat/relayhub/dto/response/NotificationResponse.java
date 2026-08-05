package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String targetUrl,
        NotificationStatus status,
        Integer retryCount,
        LocalDateTime scheduledTime,
        String lastFailureReason
) {}
