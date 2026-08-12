package dev.venkat.relayhub.controller;

import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.dto.request.ScheduleNotificationRequest;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.repository.UserRepository;
import dev.venkat.relayhub.service.JwtService;
import dev.venkat.relayhub.service.MyUserDetailsService;
import dev.venkat.relayhub.service.NotificationService;
import dev.venkat.relayhub.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;


import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MyUserDetailsService myUserDetailsService;

    private final String ENDPOINT = "/api/notifications";
    private final String IDEMPOTENCY_KEY = "idem-key-123";

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 Bad Request when Idempotency-Key header is missing")
    void scheduleNotification_WhenMissingHeader_Returns400() throws Exception {
        // Arrange: Valid JSON body
        String validJson = "{\"targetUrl\":\"https://webhook.site\",\"payload\":{\"msg\":\"hello\"}}";

        // Act & Assert: Send POST without the header
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isBadRequest());

        // Verify the service was never called
        verify(notificationService, never()).schedule(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 Bad Request when request body is missing payload or fails validation")
    void scheduleNotification_WhenInvalidBody_Returns400() throws Exception {
        // Arrange: Invalid JSON body (missing the required 'payload' field)
        String invalidJson = "{\"targetUrl\":\"https://webhook.site\"}";

        // Act & Assert: Send POST with header but invalid body
        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        // Verify the service was never called
        verify(notificationService, never()).schedule(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 201 Created and response body when request is valid")
    void scheduleNotification_WhenValidRequest_Returns201AndResponse() throws Exception {
        // Arrange
        String validJson = "{\"targetUrl\":\"https://webhook.site\",\"payload\":{\"msg\":\"hello\"}}";

        NotificationResponse mockResponse = new NotificationResponse(
                100L,
                "https://webhook.site",
                "{\"msg\":\"hello\"}",
                NotificationStatus.PENDING, // Ensuring status is PENDING as requested
                0,
                LocalDateTime.now(),
                null,
                null,
                null,
                null
        );

        when(notificationService.schedule(any(ScheduleNotificationRequest.class), eq("test@example.com"), eq(IDEMPOTENCY_KEY)))
                .thenReturn(mockResponse);

        java.security.Principal mockPrincipal = org.mockito.Mockito.mock(java.security.Principal.class);
        org.mockito.Mockito.when(mockPrincipal.getName()).thenReturn("test@example.com");

        when(notificationService.schedule(any(ScheduleNotificationRequest.class), eq("test@example.com"), eq(IDEMPOTENCY_KEY)))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(post(ENDPOINT)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson)
                        .principal(mockPrincipal))

                .andExpect(status().isCreated()) // Asserts HTTP 201
                .andExpect(jsonPath("$.id").value(100L)) // Asserts the JSON response body
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Verify the service was called exactly once
        verify(notificationService, times(1)).schedule(any(ScheduleNotificationRequest.class), eq("test@example.com"), eq(IDEMPOTENCY_KEY));
    }

    @Test
    @DisplayName("Should return 200 OK and the notification when ID is valid")
    void getNotification_WhenValidId_Returns200AndResponse() throws Exception {
        // Arrange
        Long notificationId = 100L;
        NotificationResponse mockResponse = new NotificationResponse(
                notificationId,
                "https://webhook.site",
                "{\"msg\":\"hello\"}",
                NotificationStatus.PENDING,
                0,
                LocalDateTime.now(),
                null, null, null, null
        );

        // Create the Mock Principal manually
        Principal mockPrincipal = Mockito.mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        // Tell the mock service what to return
        when(notificationService.getNotification(notificationId, "test@example.com"))
                .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get(ENDPOINT + "/{notificationId}", notificationId)
                        .principal(mockPrincipal)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Asserts HTTP 200
                .andExpect(jsonPath("$.id").value(notificationId))
                .andExpect(jsonPath("$.targetUrl").value("https://webhook.site"));

        // Verify the service was called exactly once with the correct parameters
        verify(notificationService, times(1)).getNotification(notificationId, "test@example.com");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when notificationId is not a valid number")
    void getNotification_WhenInvalidIdType_Returns400() throws Exception {
        // Arrange
        Principal mockPrincipal = Mockito.mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test@example.com");

        // Act & Assert: Send a string ("abc") instead of a Long
        mockMvc.perform(get(ENDPOINT + "/{notificationId}", "abc")
                        .principal(mockPrincipal))
                .andExpect(status().isBadRequest()); // Spring automatically throws a 400 TypeMismatchException

        // Verify the service was never called because Spring blocked the bad request
        verify(notificationService, never()).getNotification(any(), any());
    }
}
