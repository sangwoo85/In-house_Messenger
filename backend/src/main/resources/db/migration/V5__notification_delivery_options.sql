-- Presentation never changes persistence: silent notifications remain in history.
ALTER TABLE notices ADD COLUMN notification_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE notices ADD COLUMN display_mode VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE notifications ADD COLUMN notification_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE notifications ADD COLUMN display_mode VARCHAR(20) NOT NULL DEFAULT 'SYSTEM';
