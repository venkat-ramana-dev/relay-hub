package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody ScheduleNotificationRequest request,
            Principal principal) {

        String currentEmail = principal.getName();

        NotificationResponse response = notificationService.schedule(request, currentEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getNotification(
            @PathVariable Long notificationId,
            Principal principal) {

        String currentEmail = principal.getName();

        NotificationResponse response = notificationService.getNotification(notificationId, currentEmail);

        return ResponseEntity.ok(response);
    }
}