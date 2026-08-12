package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.request.AuthLoginRequest;
import dev.venkat.relayhub.dto.response.AuthLoginResponse;
import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AuthRegisterResponse;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private final String MOCK_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mockToken";

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .name("Alice Doe")
                .email("alice@example.com")
                .role(Role.USER)
                .build();
    }

    @Nested
    @DisplayName("register() Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should create user, generate token, and return response with raw API key")
        void register_WhenValidRequest_ReturnsResponse() {
            // Arrange
            AuthRegisterRequest request = new AuthRegisterRequest("Alice Doe", "alice@example.com", "SecurePass123");

            // We use eq(request) to match the DTO, and anyString() because the UUID is randomly generated inside the method
            when(userService.createUser(eq(request), anyString())).thenReturn(mockUser);
            when(jwtService.generateToken("alice@example.com")).thenReturn(MOCK_TOKEN);

            // Act
            AuthRegisterResponse response = authService.register(request);

            // Assert
            assertNotNull(response);
            assertEquals("Alice Doe", response.name());
            assertEquals("alice@example.com", response.email());
            assertEquals(Role.USER, response.role());
            assertEquals(MOCK_TOKEN, response.token());

            // Verify that the UUID API key was actually returned to the client
            assertNotNull(response.apiKey());
            assertFalse(response.apiKey().isBlank());

            verify(userService, times(1)).createUser(eq(request), anyString());
            verify(jwtService, times(1)).generateToken("alice@example.com");
        }
    }

    @Nested
    @DisplayName("login() Tests")
    class LoginTests {

        private AuthLoginRequest loginRequest;

        @BeforeEach
        void setUp() {
            loginRequest = new AuthLoginRequest("alice@example.com", "SecurePass123");
        }

        @Test
        @DisplayName("Should halt and throw exception when credentials are bad")
        void login_WhenBadCredentials_ThrowsException() {
            // Arrange
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThrows(BadCredentialsException.class, () ->
                    authService.login(loginRequest)
            );

            // Verify downstream services are never called if authentication fails
            verify(userService, never()).getUserByEmail(anyString());
            verify(jwtService, never()).generateToken(anyString());
        }

        @Test
        @DisplayName("Should authenticate, fetch user, and return login response")
        void login_WhenValidCredentials_ReturnsResponse() {
            // Arrange
            // We don't need to mock a return for authenticationManager.authenticate because it returns void/token on success

            when(userService.getUserByEmail("alice@example.com")).thenReturn(mockUser);
            when(jwtService.generateToken("alice@example.com")).thenReturn(MOCK_TOKEN);

            // Act
            AuthLoginResponse response = authService.login(loginRequest);

            // Assert
            assertNotNull(response);
            assertEquals("Alice Doe", response.name());
            assertEquals("alice@example.com", response.email());
            assertEquals(Role.USER, response.role());
            assertEquals(MOCK_TOKEN, response.token());

            // Verify the authentication manager was actually invoked with the correct credentials
            verify(authenticationManager, times(1)).authenticate(
                    argThat(token ->
                            token.getPrincipal().equals("alice@example.com") &&
                                    token.getCredentials().equals("SecurePass123")
                    )
            );
        }
    }
}
