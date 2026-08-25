package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByKeyNameAndUser(String keyName, User user);

    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(Instant time);
}
