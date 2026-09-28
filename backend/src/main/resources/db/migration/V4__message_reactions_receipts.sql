-- Permanent receipts survive leave/rejoin; unique pairs also protect concurrent retries.
CREATE TABLE message_read_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_message_reader UNIQUE (message_id, user_id),
    CONSTRAINT fk_receipt_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE message_reactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(16) CHARACTER SET utf8mb4 NOT NULL,
    CONSTRAINT uq_message_reactor UNIQUE (message_id, user_id),
    CONSTRAINT fk_reaction_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES users(id)
);
-- Preserve the known reads from the old cursor model, including users who have left.
INSERT INTO message_read_receipts (message_id, user_id, read_at)
SELECT m.id, cm.user_id, CURRENT_TIMESTAMP(6)
FROM messages m
JOIN channel_members cm ON cm.channel_id = m.channel_id
WHERE cm.last_read_message_id >= m.id
  AND cm.user_id <> m.sender_id
  AND m.is_deleted = FALSE;
