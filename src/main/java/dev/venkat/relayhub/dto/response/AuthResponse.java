package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.enums.Role;

public record AuthResponse(
        String token,
        String name,
        String email,
        Role role
) {}