package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUser_Email(Long id, String email);

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
        SELECT *
        FROM notifications
        WHERE
        (
            (status = 'PENDING' AND scheduled_time <= NOW())
            OR
            (status = 'RETRYING' AND next_retry_time <= NOW())
        )
        ORDER BY created_at
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> findPendingNotifications(@Param("batchSize") int batchSize);

    @Query(
            value = """
                SELECT *
                FROM notifications
                WHERE status = 'PROCESSING'
                  AND processing_started_at < :cutoffTime
                ORDER BY processing_started_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """,
            nativeQuery = true
    )
    List<Notification> findAndLockStuckProcessingNotifications(
            @Param("cutoffTime") Instant cutoffTime, @Param("batchSize") int batchSize
    );

}
