package dev.venkat.relayhub.worker;

import dev.venkat.relayhub.service.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessingRecoveryWorker {

    private final NotificationProcessor notificationProcessor;

    @Value("${relayhub.worker.processing-timeout:5m}")
    private Duration processingTimeout;

    @Value("${relayhub.worker.batch-size:10}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${relayhub.worker.processing-recovery-interval}"
    )
    public void recoverStuckNotifications() {

        int recoveredCount = notificationProcessor.recoverStuckNotifications(processingTimeout, batchSize);

        if (recoveredCount > 0) {
            log.warn(
                    "Recovered {} stuck PROCESSING notification(s)",
                    recoveredCount
            );
        }
    }
}
