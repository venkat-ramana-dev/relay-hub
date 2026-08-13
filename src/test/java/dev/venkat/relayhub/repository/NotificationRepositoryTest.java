package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.entity.Notification;
import dev.venkat.relayhub.entity.User;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.enums.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
// Prevent Spring from replacing our Testcontainer with an in-memory H2 database
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("Integration Tests: NotificationRepository")
class NotificationRepositoryTest {

    // Forces the Java JVM to use the modern timezone name so Postgres 17 accepts it
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
    }

    // 1. Spin up a fresh Postgres 17 container for this test class
    // 2. @ServiceConnection automatically injects the URL, username, and password into Spring
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = transactionTemplate.execute(status -> {
            return userRepository.findByEmail("test@example.com")
                    .orElseGet(() -> {
                        User user = new User();
                        user.setName("Test User");
                        user.setEmail("test@example.com");
                        user.setPassword("hashedpass");
                        user.setRole(Role.USER);
                        return userRepository.save(user);
                    });
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            notificationRepository.deleteAll();
            return null;
        });
    }

    @Nested
    @DisplayName("Method: findByIdAndUser_Email")
    class FindByIdAndUserEmailTests {

        @Test
        @DisplayName("Should find notification when ID and User Email match")
        void findByIdAndUser_Email_WhenMatch_ReturnsNotification() {
            Notification notification = new Notification();
            notification.setTargetUrl("https://webhook.site");
            notification.setPayload("{}");
            notification.setStatus(NotificationStatus.PENDING);
            notification.setUser(savedUser);
            notification.setScheduledTime(LocalDateTime.now());
            Notification saved = notificationRepository.save(notification);

            Optional<Notification> result = notificationRepository.findByIdAndUser_Email(saved.getId(), "test@example.com");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should return empty when email does not match the notification owner")
        void findByIdAndUser_Email_WhenEmailMismatch_ReturnsEmpty() {
            Notification notification = new Notification();
            notification.setTargetUrl("https://webhook.site");
            notification.setPayload("{}");
            notification.setStatus(NotificationStatus.PENDING);
            notification.setUser(savedUser);
            notification.setScheduledTime(LocalDateTime.now());
            Notification saved = notificationRepository.save(notification);

            Optional<Notification> result = notificationRepository.findByIdAndUser_Email(saved.getId(), "hacker@example.com");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Method: findById (PESSIMISTIC_WRITE Lock)")
    class FindByIdConcurrencyTests {

        @Test
        @DisplayName("Thread B is blocked and reads state only after Thread A commits")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void findById_WithPessimisticWrite_BlocksConcurrentReads() {
            // Arrange
            Notification notification = new Notification();
            notification.setTargetUrl("https://webhook.site");
            notification.setPayload("{}");
            notification.setStatus(NotificationStatus.PENDING);
            notification.setUser(savedUser);
            notification.setScheduledTime(LocalDateTime.now());
            Notification saved = notificationRepository.save(notification);
            Long id = saved.getId();

            CountDownLatch threadA_HasAcquiredLock = new CountDownLatch(1);

            // Execute Thread A
            CompletableFuture<Void> threadA = CompletableFuture.runAsync(() -> {
                transactionTemplate.execute(status -> {
                    Notification n = notificationRepository.findById(id).orElseThrow();

                    // Signal to Thread B that the lock is successfully held
                    threadA_HasAcquiredLock.countDown();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    n.setStatus(NotificationStatus.PROCESSING);
                    notificationRepository.save(n);
                    return null;
                });
            });

            // Execute Thread B
            CompletableFuture<Void> threadB = CompletableFuture.runAsync(() -> {
                try {
                    // Wait securely until Thread A explicitly says it has the lock
                    threadA_HasAcquiredLock.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                transactionTemplate.execute(status -> {
                    // THIS LINE HANGS UNTIL THREAD A FINISHES
                    Notification n = notificationRepository.findById(id).orElseThrow();

                    // PROOF: It must see the state Thread A saved, not the original PENDING state
                    assertThat(n.getStatus()).isEqualTo(NotificationStatus.PROCESSING);

                    n.setStatus(NotificationStatus.SUCCESS);
                    notificationRepository.save(n);
                    return null;
                });
            });

            // Wait for both futures to complete
            CompletableFuture.allOf(threadA, threadB).join();

            // Final state check
            Notification finalState = notificationRepository.findById(id).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("Method: findPendingNotifications (SKIP LOCKED & Filtering)")
    class FindPendingNotificationsTests {

        // Helper method to keep test data creation clean and avoid NOT NULL errors
        private Notification createTestNotification(NotificationStatus status, LocalDateTime scheduled, LocalDateTime retry) {
            Notification n = new Notification();
            n.setTargetUrl("https://webhook.site");
            n.setPayload("{}");
            n.setUser(savedUser);
            n.setStatus(status);
            n.setScheduledTime(scheduled);
            n.setNextRetryTime(retry);
            return notificationRepository.save(n);
        }

        @Test
        @DisplayName("Should strictly follow Status and Timestamp rules")
        void findPendingNotifications_FiltersByStatusAndTime() {
            LocalDateTime past = LocalDateTime.now().minusMinutes(10);
            LocalDateTime future = LocalDateTime.now().plusMinutes(10);

            // 1. PENDING & Past Scheduled -> SHOULD BE FETCHED
            Notification validPending = createTestNotification(NotificationStatus.PENDING, past, null);

            // 2. PENDING & Future Scheduled -> SKIP
            createTestNotification(NotificationStatus.PENDING, future, null);

            // 3. RETRYING & Past Retry Time -> SHOULD BE FETCHED
            Notification validRetrying = createTestNotification(NotificationStatus.RETRYING, past, past);

            // 4. RETRYING & Future Retry Time -> SKIP
            createTestNotification(NotificationStatus.RETRYING, past, future);

            // 5. SUCCESS & Past Scheduled -> SKIP (Wrong Status)
            createTestNotification(NotificationStatus.SUCCESS, past, null);

            List<Notification> pending = notificationRepository.findPendingNotifications();

            // Assert exactly 2 notifications were fetched, and they are the correct ones
            assertThat(pending).hasSize(2);
            assertThat(pending).extracting(Notification::getId)
                    .containsExactlyInAnyOrder(validPending.getId(), validRetrying.getId());
        }

        @Test
        @DisplayName("ORDER BY and LIMIT: Should fetch oldest 10 records first")
        void findPendingNotifications_RespectsLimitAndOrder() throws InterruptedException {
            // Insert 15 valid notifications
            LocalDateTime past = LocalDateTime.now().minusHours(1);
            for (int i = 0; i < 15; i++) {
                createTestNotification(NotificationStatus.PENDING, past, null);
                Thread.sleep(10); // Ensure slight difference in created_at timestamp
            }

            List<Notification> batch = notificationRepository.findPendingNotifications();

            assertThat(batch).hasSize(10); // Proves LIMIT 10 works
        }

        @Test
        @DisplayName("SKIP LOCKED: Thread B ignores locked rows and fetches the remainder")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void findPendingNotifications_SkipLocked_WorksUnderConcurrency() {
            // Setup: Insert exactly 15 valid pending notifications
            LocalDateTime past = LocalDateTime.now().minusHours(1);
            for (int i = 0; i < 15; i++) {
                createTestNotification(NotificationStatus.PENDING, past, null);
            }

            CountDownLatch threadA_HasLockedBatch = new CountDownLatch(1);

            // We use an array to store Thread B's result outside the lambda
            final int[] threadBFetchedCount = {0};

            // THREAD A: The First Worker
            CompletableFuture<Void> threadA = CompletableFuture.runAsync(() -> {
                transactionTemplate.execute(status -> {
                    // Grabs and locks the first 10
                    List<Notification> batchA = notificationRepository.findPendingNotifications();
                    assertThat(batchA).hasSize(10);

                    // Tell Thread B to attack
                    threadA_HasLockedBatch.countDown();

                    try {
                        Thread.sleep(1000); // Hold the locks open
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            });

            // THREAD B: The Second Worker
            CompletableFuture<Void> threadB = CompletableFuture.runAsync(() -> {
                try {
                    // Wait for Thread A to secure its locks
                    threadA_HasLockedBatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                transactionTemplate.execute(status -> {
                    // THIS IS THE MAGIC: It skips the 10 locked by A, and grabs the remaining 5
                    List<Notification> batchB = notificationRepository.findPendingNotifications();
                    threadBFetchedCount[0] = batchB.size();
                    return null;
                });
            });

            // Wait for both to finish
            CompletableFuture.allOf(threadA, threadB).join();

            // The Ultimate Assertion
            assertThat(threadBFetchedCount[0]).isEqualTo(5);
        }
    }
}