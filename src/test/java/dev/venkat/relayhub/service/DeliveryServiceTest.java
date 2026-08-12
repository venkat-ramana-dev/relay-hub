package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.internal.DeliveryResult;
import dev.venkat.relayhub.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    // The deep stubs answer allows us to mock the entire .post().uri()... chain!
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient webhookRestClient;

    @InjectMocks
    private DeliveryService deliveryService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = Notification.builder()
                .id(1L)
                .targetUrl("https://target.com/webhook")
                .payload("{\"key\":\"value\"}")
                .build();
    }

    @Test
    @DisplayName("Should return success DeliveryResult when webhook returns 200 OK")
    void deliver_WhenSuccessful_ReturnsSuccessResult() {
        // Arrange
        ResponseEntity<Void> mockResponseEntity = ResponseEntity.ok().build();

        // Mock the entire RestClient chain
        when(webhookRestClient.post()
                .uri(testNotification.getTargetUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header(anyString(), anyString())
                .body(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenReturn(mockResponseEntity);

        // Act
        DeliveryResult result = deliveryService.deliver(testNotification);

        // Assert
        assertTrue(result.success());
        assertEquals(200, result.statusCode());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should return failure DeliveryResult when webhook returns 400/500 Error")
    void deliver_WhenHttpError_ReturnsFailureResultWithBodySnippet() {
        // Arrange
        String errorBody = "Invalid payload format provided";
        HttpClientErrorException httpError = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                errorBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(webhookRestClient.post()
                .uri(anyString())
                .contentType(any())
                .header(anyString(), anyString())
                .body(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenThrow(httpError);

        // Act
        DeliveryResult result = deliveryService.deliver(testNotification);

        // Assert
        assertFalse(result.success());
        assertEquals(400, result.statusCode());
        assertEquals("HTTP 400: Invalid payload format provided", result.errorMessage());
    }

    @Test
    @DisplayName("Should truncate error body if it exceeds 200 characters")
    void deliver_WhenHttpErrorBodyIsHuge_TruncatesMessage() {
        // Arrange
        String hugeBody = "A".repeat(300); // Create a 300-character string
        HttpServerErrorException httpError = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                hugeBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(webhookRestClient.post()
                .uri(anyString())
                .contentType(any())
                .header(anyString(), anyString())
                .body(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenThrow(httpError);

        // Act
        DeliveryResult result = deliveryService.deliver(testNotification);

        // Assert
        assertFalse(result.success());
        assertEquals(500, result.statusCode());
        assertTrue(result.errorMessage().endsWith("..."));
        assertTrue(result.errorMessage().length() < 225); // "HTTP 500: " + 200 chars + "..."
    }

    @Test
    @DisplayName("Should return failure DeliveryResult when network times out")
    void deliver_WhenNetworkTimeout_ReturnsFailureResult() {
        // Arrange
        when(webhookRestClient.post()
                .uri(anyString())
                .contentType(any())
                .header(anyString(), anyString())
                .body(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenThrow(new ResourceAccessException("Connection timed out"));

        // Act
        DeliveryResult result = deliveryService.deliver(testNotification);

        // Assert
        assertFalse(result.success());
        assertNull(result.statusCode());
        assertTrue(result.errorMessage().contains("Network/Timeout error"));
    }

    @Test
    @DisplayName("Should return failure DeliveryResult on generic unexpected exceptions")
    void deliver_WhenGenericException_ReturnsFailureResult() {
        // Arrange
        when(webhookRestClient.post()
                .uri(anyString())
                .contentType(any())
                .header(anyString(), anyString())
                .body(anyString())
                .retrieve()
                .toBodilessEntity())
                .thenThrow(new RuntimeException("Something completely unexpected happened"));

        // Act
        DeliveryResult result = deliveryService.deliver(testNotification);

        // Assert
        assertFalse(result.success());
        assertNull(result.statusCode());
        assertTrue(result.errorMessage().contains("Unexpected error"));
    }
}
