package dev.venkat.relayhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"key_name", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_name", nullable = false, updatable = false)
    private String keyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
