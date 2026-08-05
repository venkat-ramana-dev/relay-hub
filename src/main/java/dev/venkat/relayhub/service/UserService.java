package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.CreateUserRequest;
import dev.venkat.relayhub.dto.response.CreateUserResponse;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.exception.DuplicateEmailException;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {

        log.info("Creating new user with email: {}",request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already exists: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(request.password())
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        log.info("New User successfully created with id: {}",savedUser.getId());

        return CreateUserResponse.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build();
    }

}
