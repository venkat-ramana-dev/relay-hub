ALTER TABLE notifications
    ADD COLUMN processing_started_at TIMESTAMPTZ NULL;

CREATE INDEX idx_notifications_stuck_processing
    ON notifications (processing_started_at)
    WHERE status = 'PROCESSING';