package dev.venkat.relayhub.mapper;

import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMapper {

    private final ObjectMapper objectMapper;

    public NotificationResponse mapToResponse(Notification notification) {
        JsonNode payloadNode;

        try {
            payloadNode = objectMapper.readTree(notification.getPayload());
        } catch (Exception e) {
            log.error("Failed to parse JSON payload for notification {}", notification.getId(), e);
            throw new IllegalStateException(
                    "Notification contains invalid JSON payload",
                    e
            );
        }

        return new NotificationResponse(
                notification.getId(),
                notification.getTargetUrl(),
                payloadNode,
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