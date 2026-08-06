package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.exception.DuplicateEmailException;
import dev.venkat.relayhub.exception.UserNotFoundException;
import dev.venkat.relayhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(AuthRegisterRequest request) {

        log.info("Creating new user with email: {}",request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already exists: " + request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        log.info("New User successfully created with id: {}",savedUser.getId());

        return savedUser;
    }

    @Transactional
    public User createAdmin(AuthRegisterRequest request) {
        log.info("Creating new ADMIN with email {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already exists: " + request.email());
        }

        User admin = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build();

        User savedAdmin = userRepository.save(admin);
        log.info("New ADMIN successfully created with id: {}", savedAdmin.getId());

        return savedAdmin;
    }

    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

}
