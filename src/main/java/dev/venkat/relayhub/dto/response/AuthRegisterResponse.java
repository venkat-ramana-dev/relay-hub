package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.enums.Role;

public record AuthRegisterResponse(
        String token,
        String name,
        String email,
        Role role,
        String apiKey
) {}
