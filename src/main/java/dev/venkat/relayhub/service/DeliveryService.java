package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final RestClient webhookRestClient;

    public DeliveryResult deliver(Notification notification) {
        log.info("Attempting delivery for Notification ID: {} to URL: {}",
                notification.getId(), notification.getTargetUrl());

        try {

            ResponseEntity<Void> response = webhookRestClient.post()
                    .uri(notification.getTargetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-RelayHub-Notification-Id", String.valueOf(notification.getId()))
                    .body(notification.getPayload())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Delivery successful for Notification ID: {} with Status: {}",
                    notification.getId(), response.getStatusCode().value());

            return DeliveryResult.success(response.getStatusCode().value());

        } catch (HttpClientErrorException | HttpServerErrorException e) {

            String rawBody = e.getResponseBodyAsString();

            String bodySnippet = (rawBody != null && !rawBody.isBlank())
                    ? rawBody
                    : "No response body provided";

            if (bodySnippet.length() > 200) {
                bodySnippet = bodySnippet.substring(0, 200) + "...";
            }

            String errorMessage = String.format("HTTP %d: %s", e.getStatusCode().value(), bodySnippet);

            log.warn("Target server rejected Notification ID: {} with status: {}",
                    notification.getId(), e.getStatusCode().value());

            return DeliveryResult.failure(e.getStatusCode().value(), errorMessage);

        } catch (ResourceAccessException e) {
            log.error("Network error delivering Notification ID: {}: {}",
                    notification.getId(), e.getMessage());
            return DeliveryResult.failure(null, "Network/Timeout error: " + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error delivering Notification ID: {}", notification.getId(), e);
            return DeliveryResult.failure(null, "Unexpected error: " + e.getMessage());
        }
    }
}
