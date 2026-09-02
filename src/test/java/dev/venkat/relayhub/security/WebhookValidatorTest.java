package dev.venkat.relayhub.security;

import dev.venkat.relayhub.exception.UnsafeWebhookUrlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void shouldAllowHttpsUrl() {
        assertDoesNotThrow(() ->
                validator.validate("https://example.com"));
    }

    @Test
    void shouldAllowHttpUrl() {
        assertDoesNotThrow(() ->
                validator.validate("http://example.com"));
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThrows(
                UnsafeWebhookUrlException.class,
                () -> validator.validate("ftp://example.com")
        );
    }

    @Test
    void shouldRejectLocalhost() {
        assertThrows(
                UnsafeWebhookUrlException.class,
                () -> validator.validate("http://localhost")
        );
    }

    @Test
    void shouldRejectLoopbackAddress() {
        assertThrows(
                UnsafeWebhookUrlException.class,
                () -> validator.validate("http://127.0.0.1")
        );
    }

    @Test
    void shouldRejectPrivateNetworkAddress() {
        assertThrows(
                UnsafeWebhookUrlException.class,
                () -> validator.validate("http://192.168.1.10")
        );
    }
}