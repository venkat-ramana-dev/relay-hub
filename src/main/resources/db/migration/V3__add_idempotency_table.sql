CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    key_name VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    notification_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_idempotency_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
    CONSTRAINT uq_key_user UNIQUE (key_name, user_id)
);