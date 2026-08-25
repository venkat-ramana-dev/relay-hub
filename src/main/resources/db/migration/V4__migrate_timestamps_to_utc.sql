-- Convert all application timestamps from TIMESTAMP to TIMESTAMPTZ.
-- Existing TIMESTAMP values are interpreted as UTC during migration.

ALTER TABLE users
ALTER COLUMN created_at TYPE TIMESTAMPTZ
    USING created_at AT TIME ZONE 'UTC';

ALTER TABLE notifications
ALTER COLUMN scheduled_time TYPE TIMESTAMPTZ
    USING scheduled_time AT TIME ZONE 'UTC';

ALTER TABLE notifications
ALTER COLUMN next_retry_time TYPE TIMESTAMPTZ
    USING next_retry_time AT TIME ZONE 'UTC';

ALTER TABLE notifications
ALTER COLUMN created_at TYPE TIMESTAMPTZ
    USING created_at AT TIME ZONE 'UTC';

ALTER TABLE notifications
ALTER COLUMN updated_at TYPE TIMESTAMPTZ
    USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE notification_history
ALTER COLUMN changed_at TYPE TIMESTAMPTZ
    USING changed_at AT TIME ZONE 'UTC';

ALTER TABLE idempotency_records
ALTER COLUMN created_at TYPE TIMESTAMPTZ
    USING created_at AT TIME ZONE 'UTC';