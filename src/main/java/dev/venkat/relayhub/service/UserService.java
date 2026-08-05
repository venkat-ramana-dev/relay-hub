package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.CreateUserRequest;
import dev.venkat.relayhub.dto.response.CreateUserResponse;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already exists.");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(request.password())
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        return CreateUserResponse.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build();
    }

}
