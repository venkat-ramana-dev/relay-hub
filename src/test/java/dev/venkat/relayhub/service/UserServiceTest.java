package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.exception.DuplicateEmailException;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("createUser() Tests")
    class CreateUserTests {

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

    @Nested
    @DisplayName("createAdmin() Tests")
    class CreateAdminTests {

        @Test
        @DisplayName("Should throw DuplicateEmailException when admin email already exists")
        void createAdmin_WhenEmailExists_ThrowsException() {
            // Arrange - using standard placeholder data
            AuthRegisterRequest adminRequest = new AuthRegisterRequest("Admin User", "admin@example.com", "SecureAdminPass!");
            when(userRepository.existsByEmail(adminRequest.email())).thenReturn(true);

            // Act & Assert
            DuplicateEmailException exception = assertThrows(DuplicateEmailException.class, () ->
                    userService.createAdmin(adminRequest, RAW_API_KEY)
            );

            assertEquals("Email already exists: admin@example.com", exception.getMessage());
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should encode password, hash API key, and save user with ADMIN role")
        void createAdmin_WhenValidRequest_SavesAndReturnsAdmin() {
            // Arrange
            AuthRegisterRequest adminRequest = new AuthRegisterRequest("System Admin", "sysadmin@example.com", "SecureAdminPass!");

            when(userRepository.existsByEmail(adminRequest.email())).thenReturn(false);
            when(passwordEncoder.encode(adminRequest.password())).thenReturn("Encoded_AdminPass");

            User mockSavedAdmin = User.builder().id(99L).role(Role.ADMIN).build();
            when(userRepository.save(any(User.class))).thenReturn(mockSavedAdmin);

            // Act
            User result = userService.createAdmin(adminRequest, RAW_API_KEY);

            // Assert method return value
            assertNotNull(result);
            assertEquals(99L, result.getId());

            // Capture the object sent to the database to verify the Role assignment
            verify(userRepository).save(userCaptor.capture());
            User capturedAdmin = userCaptor.getValue();

            // Verify the internal builder logic
            assertEquals("System Admin", capturedAdmin.getName());
            assertEquals("sysadmin@example.com", capturedAdmin.getEmail());
            assertEquals("Encoded_AdminPass", capturedAdmin.getPassword());

            // THIS IS THE CRITICAL ASSERTION: Proves they got the Admin role!
            assertEquals(Role.ADMIN, capturedAdmin.getRole());

            assertEquals(SecurityUtil.hashApiKey(RAW_API_KEY), capturedAdmin.getApiKey());
        }
    }
}
