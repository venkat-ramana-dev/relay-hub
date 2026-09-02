package dev.venkat.relayhub.exception;

public class IdempotencyRaceRecoveryException extends RuntimeException {
    public IdempotencyRaceRecoveryException(String message) {
        super(message);
    }
}
