package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.enums.Role;

public record AuthResponse(
        String token,
        Long id,
        String name,
        String email,
        Role role
) {}