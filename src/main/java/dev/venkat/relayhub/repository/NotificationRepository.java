package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndUser_Email(Long id, String email);

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findById(Long id);

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
        LIMIT 10
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Notification> findPendingNotifications();

}
