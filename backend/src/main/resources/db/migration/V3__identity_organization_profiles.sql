ALTER TABLE users ADD COLUMN department_id VARCHAR(100);
ALTER TABLE users ADD COLUMN profile_image_key VARCHAR(36);
CREATE TABLE organization_snapshots (
    id INTEGER NOT NULL PRIMARY KEY,
    data_json LONGTEXT NOT NULL,
    synced_at DATETIME(6) NOT NULL
);
