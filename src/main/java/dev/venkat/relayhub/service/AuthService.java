package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthLoginRequest;
import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AuthRegisterResponse;
import dev.venkat.relayhub.dto.response.AuthLoginResponse;
import dev.venkat.relayhub.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthRegisterResponse register(AuthRegisterRequest request) {

        log.info("Processing registration for email: {}", request.email());

        String rawApiKey = UUID.randomUUID().toString().replace("-", "");

        User user = userService.createUser(request, rawApiKey);
        String token = jwtService.generateToken(user.getEmail());
        return new AuthRegisterResponse(token, user.getName(), user.getEmail(), user.getRole(), rawApiKey);
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        log.info("Processing login for email: {}", request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userService.getUserByEmail(request.email());
        String token = jwtService.generateToken(user.getEmail());
        return new AuthLoginResponse(token, user.getName(), user.getEmail(), user.getRole());
    }
}
