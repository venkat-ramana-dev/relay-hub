package dev.venkat.relayhub.dto.response;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String targetUrl,
        String status,
        Integer retryCount,
        LocalDateTime scheduledTime,
        String lastFailureReason
) {}
