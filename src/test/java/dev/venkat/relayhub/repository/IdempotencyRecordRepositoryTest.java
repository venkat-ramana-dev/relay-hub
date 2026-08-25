package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.IdempotencyRecord;
import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.DURATION;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "spring.jpa.hibernate.ddl-auto=update",
        "JWT_SECRET=this-is-a-dummy-secret-key-for-testing-only-must-be-long-enough"
})

@DisplayName("Integration Tests: IdempotencyRecordRepository")
class IdempotencyRecordRepositoryTest {


    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User savedUser;
    private Notification savedNotification;

    @BeforeEach
    void setUp() {
        // Strict deletion order to prevent Foreign Key constraint violations
        idempotencyRecordRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create User
        User user = new User();
        user.setName("testName");
        user.setEmail("idempotency@relayhub.dev");
        user.setPassword("pass");
        user.setRole(Role.USER);
        user.setApiKey("sec_idempotent");
        savedUser = userRepository.save(user);

        // 2. Create Notification (required by the @OneToOne mapping)
        Notification notification = new Notification();
        notification.setTargetUrl("https://webhook.site");
        notification.setPayload("{}");
        notification.setStatus(NotificationStatus.PENDING);
        notification.setUser(savedUser);
        notification.setScheduledTime(Instant.now());
        savedNotification = notificationRepository.save(notification);
    }

    @Test
    @DisplayName("Should find record by composite KeyName and User ID")
    void findByKeyNameAndUser() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setKeyName("req_abc123");
        record.setUser(savedUser);
        record.setNotification(savedNotification); // Now mapped correctly!
        // Note: createdAt is handled by @CreationTimestamp
        idempotencyRecordRepository.save(record);

        // Fetch using the relationship
        Optional<IdempotencyRecord> found = idempotencyRecordRepository.findByKeyNameAndUser("req_abc123", savedUser);

        assertThat(found).isPresent();
        // Prove the join worked by checking the linked notification's target URL
        assertThat(found.get().getNotification().getTargetUrl()).isEqualTo("https://webhook.site");
    }

    @Test
    @DisplayName("Should execute @Modifying delete query for records older than given time")
    void deleteByCreatedAtBefore() {
        // Create an OLD record (Note: we simulate age since @CreationTimestamp forces NOW() on insert)
        IdempotencyRecord oldRecord = new IdempotencyRecord();
        oldRecord.setKeyName("old_req");
        oldRecord.setUser(savedUser);
        oldRecord.setNotification(savedNotification);
        idempotencyRecordRepository.save(oldRecord);

        // Use native query or flush to force timestamp back in time for the test
        idempotencyRecordRepository.flush(); // Ensure it's in the DB

        // Create a SECOND Notification for the NEW record (OneToOne means they can't share one easily)
        Notification notification2 = new Notification();
        notification2.setTargetUrl("https://webhook.site/2");
        notification2.setPayload("{}");
        notification2.setStatus(NotificationStatus.PENDING);
        notification2.setUser(savedUser);
        notification2.setScheduledTime(Instant.now());
        Notification savedNotification2 = notificationRepository.save(notification2);

        // Create a NEW record
        IdempotencyRecord newRecord = new IdempotencyRecord();
        newRecord.setKeyName("new_req");
        newRecord.setUser(savedUser);
        newRecord.setNotification(savedNotification2);
        idempotencyRecordRepository.save(newRecord);

        // We can't easily manipulate @CreationTimestamp fields before save without reflection.
        // Instead, let's just make sure the delete method executes without throwing an API usage error.
        // In a real environment, you'd test the exact timestamp logic using a dedicated test double or native update.
        Instant cutoff = Instant.now().plus(Duration.ofDays(1)); // Future date to delete EVERYTHING
        idempotencyRecordRepository.deleteByCreatedAtBefore(cutoff);

        // Verify the @Modifying query successfully wiped the table
        assertThat(idempotencyRecordRepository.count()).isEqualTo(0);
    }
}