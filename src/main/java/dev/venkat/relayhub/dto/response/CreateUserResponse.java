package dev.venkat.relayhub.dto.response;

import dev.venkat.relayhub.enums.Role;
import lombok.Builder;

@Builder
public record CreateUserResponse (
        Long id,
        String name,
        String email,
        Role role
) {}
