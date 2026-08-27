package dev.venkat.relayhub.dto.response;

public record SystemMetricsResponse(
        long totalPending,
        long totalRetrying,
        long totalSuccess,
        long totalDead

) {}