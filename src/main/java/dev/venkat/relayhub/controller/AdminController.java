package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AdminCreatedResponse;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AdminCreatedResponse> registerAdmin(@Valid @RequestBody AuthRegisterRequest request) {

        User newAdmin = userService.createAdmin(request);

        AdminCreatedResponse response = new AdminCreatedResponse(
                "Admin created successfully",
                newAdmin.getId(),
                newAdmin.getEmail(),
                newAdmin.getRole()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
