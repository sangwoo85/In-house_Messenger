ALTER TABLE messages ADD COLUMN reply_to_message_id BIGINT NULL;
CREATE INDEX idx_message_reply ON messages(reply_to_message_id);
ALTER TABLE messages ADD CONSTRAINT fk_message_reply
    FOREIGN KEY (reply_to_message_id) REFERENCES messages(id);
