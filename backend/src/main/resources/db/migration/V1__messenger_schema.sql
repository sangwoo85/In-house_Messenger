CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500),
    department VARCHAR(100),
    user_group VARCHAR(100),
    status ENUM('ONLINE','OFFLINE','AWAY') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_users_external_id UNIQUE (user_id)
);
CREATE TABLE channels (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    type ENUM('DM','GROUP') NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_channel_creator FOREIGN KEY (created_by) REFERENCES users(id)
);
CREATE TABLE files (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
);
CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    sender_id BIGINT,
    content TEXT,
    type ENUM('TEXT','IMAGE','FILE','SYSTEM','NOTICE','EXTERNAL') NOT NULL,
    file_id BIGINT,
    is_deleted BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_message_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_message_file FOREIGN KEY (file_id) REFERENCES files(id)
);
CREATE TABLE channel_members (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('OWNER','MEMBER') NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6),
    last_read_message_id BIGINT,
    CONSTRAINT uq_channel_user UNIQUE (channel_id, user_id),
    CONSTRAINT fk_member_channel FOREIGN KEY (channel_id) REFERENCES channels(id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_member_last_read FOREIGN KEY (last_read_message_id) REFERENCES messages(id)
);
CREATE TABLE notices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    sender VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    link_url VARCHAR(500),
    is_read BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
