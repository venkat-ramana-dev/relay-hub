package dev.venkat.relayhub.worker;

import dev.venkat.relayhub.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupWorker {

    private final IdempotencyRecordRepository idempotencyRepository;

    // Runs at the top of every hour
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupOldIdempotencyKeys() {

        log.info("Starting scheduled cleanup of expired idempotency keys...");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

        try {
            idempotencyRepository.deleteByCreatedAtBefore(cutoffTime);
            log.info("Successfully purged idempotency keys older than 24 hours.");
        } catch (Exception e) {
            log.error("Failed to clean up old idempotency keys", e);
        }
    }
}
