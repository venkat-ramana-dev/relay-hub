package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.request.AuthRegisterRequest;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.service.JwtService;
import dev.venkat.relayhub.service.UserService;
import dev.venkat.relayhub.service.MyUserDetailsService;
import dev.venkat.relayhub.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false) // Safely bypass filters for auth testing
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MyUserDetailsService myUserDetailsService;

    @Test
    @DisplayName("Should return 201 Created and generate random API key when request is valid")
    void registerAdmin_WhenValidRequest_Returns201() throws Exception {
        // Arrange
        String validJson = "{\"name\":\"Admin Boss\",\"email\":\"admin@example.com\",\"password\":\"secure123\"}";

        dev.venkat.relayhub.entity.User mockAdmin = Mockito.mock(dev.venkat.relayhub.entity.User.class);
        when(mockAdmin.getId()).thenReturn(1L);
        when(mockAdmin.getEmail()).thenReturn("admin@example.com");
        when(mockAdmin.getRole()).thenReturn(Role.ADMIN);

        when(userService.createAdmin(any(AuthRegisterRequest.class), anyString()))
                .thenReturn(mockAdmin);

        // Act & Assert
        mockMvc.perform(post("/api/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Admin created successfully"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                // We can't know the exact UUID, but we CAN prove the controller attached it to the response!
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.apiKey").isString());

        // Verify
        verify(userService, times(1)).createAdmin(any(AuthRegisterRequest.class), anyString());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when request body is invalid")
    void registerAdmin_WhenInvalidRequest_Returns400() throws Exception {

        String invalidJson = "{\"email\":\"not-an-email\",\"password\":\"secure123\"}";

        // Act & Assert
        mockMvc.perform(post("/api/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        // Prove the controller's @Valid annotation blocked the request before hitting the service
        verify(userService, never()).createAdmin(any(), any());
    }
}
