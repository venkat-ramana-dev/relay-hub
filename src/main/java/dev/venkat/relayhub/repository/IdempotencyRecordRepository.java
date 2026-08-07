package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByKeyNameAndUser(String keyName, User user);
}
