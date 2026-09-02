package dev.venkat.relayhub.exception;

public class UnsafeWebhookUrlException extends RuntimeException {

    public UnsafeWebhookUrlException(String message) {
        super(message);
    }
}