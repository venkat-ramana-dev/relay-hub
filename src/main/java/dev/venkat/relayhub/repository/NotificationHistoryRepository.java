package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {
}