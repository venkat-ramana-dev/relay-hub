package dev.venkat.relayhub.dto.internal;

public record DeliveryResult(
        boolean success,
        Integer statusCode,
        String errorMessage
) {
    public static DeliveryResult success(int statusCode) {
        return new DeliveryResult(true, statusCode, null);
    }

    public static DeliveryResult failure(Integer statusCode, String errorMessage) {
        return new DeliveryResult(false, statusCode, errorMessage);
    }
}
