package dev.venkat.relayhub.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.venkat.relayhub.enums.NotificationStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        Long id,
        String targetUrl,
        String payload,
        NotificationStatus status,
        Integer retryCount,
        Instant scheduledTime,
        Instant nextRetryTime,
        String lastFailureReason,
        Instant createdAt,
        Instant updatedAt
) {}
