package dev.venkat.relayhub.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record ScheduleNotificationRequest(

        @NotBlank(message = "Target URL cannot be blank")
        @URL(message = "Target URL must be a valid HTTP/HTTPS URL")
        String targetUrl,

        @NotNull(message = "Payload cannot be null")
        JsonNode payload,

        @FutureOrPresent(message = "Scheduled time cannot be in the past")
        Instant scheduledTime
) {}
