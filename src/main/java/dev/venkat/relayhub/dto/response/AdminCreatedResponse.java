package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.enums.Role;

public record AdminCreatedResponse(
        String message,
        Long id,
        String email,
        Role role,
        String apiKey
) {}
