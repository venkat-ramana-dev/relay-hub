package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @Valid @RequestBody ScheduleNotificationRequest request) {

        NotificationResponse response = notificationService.schedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
