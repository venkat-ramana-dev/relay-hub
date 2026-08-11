package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.exception.DuplicateEmailException;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // The ArgumentCaptor lets us "grab" the object passed to a mock method
    @Captor
    private ArgumentCaptor<User> userCaptor;

    private AuthRegisterRequest request;
    private final String RAW_API_KEY = "my-secret-api-key";

    @BeforeEach
    void setUp() {
        request = new AuthRegisterRequest("John Doe", "john.doe@example.com", "SecurePassword123!");
    }

    @Test
    @DisplayName("Should throw DuplicateEmailException when email already exists")
    void createUser_WhenEmailExists_ThrowsException() {
        // Arrange
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // Act & Assert
        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class, () ->
                userService.createUser(request, RAW_API_KEY)
        );

        assertEquals("Email already exists: john.doe@example.com", exception.getMessage());

        // Verify downstream dependencies were untouched
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should encode password, hash API key, and save user when request is valid")
    void createUser_WhenValidRequest_SavesAndReturnsUser() {
        // Arrange
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("Encoded_MyPassword123");

        User mockSavedUser = User.builder().id(1L).build();
        when(userRepository.save(any(User.class))).thenReturn(mockSavedUser);

        // Act
        User result = userService.createUser(request, RAW_API_KEY);

        // Assert 1: Did the method return the correct saved user from the database?
        assertNotNull(result);
        assertEquals(1L, result.getId());

        // Assert 2: Capture the exact User object that was passed into userRepository.save()
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        // Assert 3: Verify the internal builder logic is flawless
        assertEquals("John Doe", capturedUser.getName());
        assertEquals("john.doe@example.com", capturedUser.getEmail());
        assertEquals("Encoded_MyPassword123", capturedUser.getPassword());
        assertEquals(Role.USER, capturedUser.getRole());

        // Proves the static utility method worked
        assertNotNull(capturedUser.getApiKey());
        assertEquals(SecurityUtil.hashApiKey(RAW_API_KEY), capturedUser.getApiKey());
    }
}
