CREATE INDEX idx_notifications_retry
ON notifications(status, next_retry_time);