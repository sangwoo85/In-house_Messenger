ALTER TABLE channels ADD COLUMN direct_key VARCHAR(64);
ALTER TABLE channels ADD CONSTRAINT uq_channel_direct_key UNIQUE (direct_key);
ALTER TABLE messages ADD COLUMN client_request_id VARCHAR(36);
ALTER TABLE messages ADD CONSTRAINT uq_message_request UNIQUE (sender_id, client_request_id);
CREATE INDEX idx_messages_channel_cursor ON messages(channel_id, id);
CREATE INDEX idx_members_user_active ON channel_members(user_id, left_at);
CREATE INDEX idx_notifications_user_read_created ON notifications(user_id, is_read, created_at);
CREATE INDEX idx_notices_created ON notices(created_at, id);
