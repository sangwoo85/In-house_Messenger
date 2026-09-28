CREATE TABLE channel_schedules (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 channel_id BIGINT NOT NULL,
 creator VARCHAR(50) NOT NULL,
 title VARCHAR(200) NOT NULL,
 start_at BIGINT NOT NULL,
 end_at BIGINT NOT NULL,
 location VARCHAR(100),
 audience VARCHAR(10) NOT NULL,
 reminder_minutes INT NOT NULL,
 revision BIGINT NOT NULL,
 CONSTRAINT fk_schedule_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
 CONSTRAINT fk_schedule_creator FOREIGN KEY (creator) REFERENCES users(user_id)
);
CREATE INDEX ix_schedule_channel ON channel_schedules(channel_id, start_at);
CREATE TABLE schedule_reminders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 schedule_id BIGINT NOT NULL,
 recipient VARCHAR(50) NOT NULL,
 due_at BIGINT NOT NULL,
 acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_reminder_schedule FOREIGN KEY (schedule_id) REFERENCES channel_schedules(id) ON DELETE CASCADE,
 CONSTRAINT fk_reminder_recipient FOREIGN KEY (recipient) REFERENCES users(user_id),
 CONSTRAINT uq_schedule_recipient UNIQUE (schedule_id, recipient)
);
CREATE INDEX ix_reminder_due ON schedule_reminders(recipient, acknowledged, due_at);
