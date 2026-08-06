package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthLoginRequest;
import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AuthResponse;
import dev.venkat.relayhub.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(AuthRegisterRequest request) {

        log.info("Processing registration for email: {}", request.email());

        User user = userService.createUser(request);
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole());
    }

    public AuthResponse login(AuthLoginRequest request) {
        log.info("Processing login for email: {}", request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userService.getUserByEmail(request.email());
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole());
    }
}
