package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AdminCreatedResponse;
import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.dto.response.SystemMetricsResponse;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.service.AdminService;
import dev.venkat.relayhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    private final AdminService adminService;

    @PostMapping("/register")
    public ResponseEntity<AdminCreatedResponse> registerAdmin(@Valid @RequestBody AuthRegisterRequest request) {

        String rawApiKey = UUID.randomUUID().toString().replace("-", "");

        User newAdmin = userService.createAdmin(request, rawApiKey);

        AdminCreatedResponse response = new AdminCreatedResponse(
                "Admin created successfully",
                newAdmin.getId(),
                newAdmin.getEmail(),
                newAdmin.getRole(),
                rawApiKey
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<SystemMetricsResponse> getSystemMetrics() {
        return ResponseEntity.ok(adminService.getSystemMetrics());
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<NotificationResponse>> getAllNotifications(
            @PageableDefault(
                    size = 50,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable) {

        return ResponseEntity.ok(adminService.getAllSystemNotifications(pageable));
    }
}
