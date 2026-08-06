package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @Valid @RequestBody ScheduleNotificationRequest request) {

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        NotificationResponse response = notificationService.schedule(request, currentEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> getNotification(
            @PathVariable Long notificationId) {

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        NotificationResponse response = notificationService.getNotification(notificationId, currentEmail);

        return ResponseEntity.ok(response);
    }
}
