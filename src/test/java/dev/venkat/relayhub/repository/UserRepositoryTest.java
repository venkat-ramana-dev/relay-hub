package dev.venkat.relayhub.repository;

import dev.venkat.relayhub.enums.Role;
import dev.venkat.relayhub.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "POSTGRES_USER=test",
        "POSTGRES_PASSWORD=test",
        "POSTGRES_DB=test",
        "spring.jpa.hibernate.ddl-auto=update",
        "JWT_SECRET=dGhpcy1pcy1hLWR1bW15LXNlY3JldC1rZXktZm9yLXRlc3Rpbmc="
})
@DisplayName("Integration Tests: UserRepository")
class UserRepositoryTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should successfully save and fetch a User by Email and API Key")
    void saveAndFetchUser() {
        // 1. Save
        User user = new User();
        user.setName("testName");
        user.setEmail("test@relayhub.dev");
        user.setPassword("hashed_password");
        user.setRole(Role.USER);
        user.setApiKey("sec_123456789");
        userRepository.save(user);

        // 2. Test findByEmail & existsByEmail
        assertThat(userRepository.existsByEmail("test@relayhub.dev")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@relayhub.dev")).isFalse();

        Optional<User> foundByEmail = userRepository.findByEmail("test@relayhub.dev");
        assertThat(foundByEmail).isPresent();
        assertThat(foundByEmail.get().getId()).isEqualTo(user.getId());

        // 3. Test findByApiKey
        Optional<User> foundByApiKey = userRepository.findByApiKey("sec_123456789");
        assertThat(foundByApiKey).isPresent();
        assertThat(foundByApiKey.get().getEmail()).isEqualTo("test@relayhub.dev");

        // 4. Test existsByRole
        assertThat(userRepository.existsByRole(Role.USER)).isTrue();
    }
}