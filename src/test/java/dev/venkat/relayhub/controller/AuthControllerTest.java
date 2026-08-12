package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.AuthLoginRequest;
import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.dto.response.AuthLoginResponse;
import dev.venkat.relayhub.dto.response.AuthRegisterResponse;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.service.AuthService;
import dev.venkat.relayhub.service.JwtService;
import dev.venkat.relayhub.service.UserService;
import dev.venkat.relayhub.service.MyUserDetailsService;
import dev.venkat.relayhub.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Safely bypass filters for auth testing
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- MAIN CONTROLLER MOCK ---
    @MockitoBean
    private AuthService authService;

    // --- SECURITY CONTEXT MOCKS (Prevents context crashes) ---
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MyUserDetailsService myUserDetailsService;

    @Test
    @DisplayName("Register: Should return 201 Created when request is valid")
    void register_WhenValidRequest_Returns201() throws Exception {
        String validJson = "{\"name\":\"John Doe\",\"email\":\"john@example.com\",\"password\":\"secret123\"}";

        AuthRegisterResponse mockResponse = new AuthRegisterResponse("mock-jwt-token", "John Doe", "john@example.com", Role.USER, "api_key");
        when(authService.register(any(AuthRegisterRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock-jwt-token")); // Adjust "$.token" based on your actual response DTO field name

        verify(authService, times(1)).register(any(AuthRegisterRequest.class));
    }

    @Test
    @DisplayName("Register: Should return 400 Bad Request when name is missing")
    void register_WhenNameIsMissing_Returns400() throws Exception {
        // Missing the "name" field
        String invalidJson = "{\"email\":\"john@example.com\",\"password\":\"secret123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        // Prove the controller blocked the request before hitting the service
        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("Register: Should return 400 Bad Request when email is invalid")
    void register_WhenEmailIsInvalid_Returns400() throws Exception {
        // "not-an-email" fails the @Email validation
        String invalidJson = "{\"name\":\"John Doe\",\"email\":\"not-an-email\",\"password\":\"secret123\"}";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    // ==========================================
    // LOGIN ENDPOINT TESTS
    // ==========================================

    @Test
    @DisplayName("Login: Should return 200 OK when request is valid")
    void login_WhenValidRequest_Returns200() throws Exception {
        String validJson = "{\"email\":\"john@example.com\",\"password\":\"secret123\"}";

        AuthLoginResponse mockResponse = new AuthLoginResponse("mock-jwt-token", "John Doe", "john@example.com", Role.USER);
        when(authService.login(any(AuthLoginRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token")); // Adjust "$.token" based on your actual response DTO field name

        verify(authService, times(1)).login(any(AuthLoginRequest.class));
    }

    @Test
    @DisplayName("Login: Should return 400 Bad Request when password is missing")
    void login_WhenPasswordIsMissing_Returns400() throws Exception {
        // Missing the "password" field
        String invalidJson = "{\"email\":\"john@example.com\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }
}