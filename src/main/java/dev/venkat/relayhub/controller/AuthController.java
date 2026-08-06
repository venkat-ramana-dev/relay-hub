package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.AuthLoginRequest;
import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AuthLoginResponse;
import dev.venkat.relayhub.dto.response.AuthRegisterResponse;
import dev.venkat.relayhub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthRegisterResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
