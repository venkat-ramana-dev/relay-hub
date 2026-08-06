package dev.venkat.relayhub.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.venkat.relayhub.enums.NotificationStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        Long id,
        String targetUrl,
        String payload,
        NotificationStatus status,
        Integer retryCount,
        LocalDateTime scheduledTime,
        LocalDateTime nextRetryTime,
        String lastFailureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
