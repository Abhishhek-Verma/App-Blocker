-- Schema for App Blocker Database

CREATE TABLE IF NOT EXISTS blocked_apps (
    id SERIAL PRIMARY KEY,
    package_name VARCHAR(255) UNIQUE NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    is_blocked BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_settings (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE DEFAULT 'default_device',
    pin_code VARCHAR(10) DEFAULT NULL,
    require_pin_to_unlock BOOLEAN DEFAULT FALSE,
    master_blocker_active BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS block_events (
    id SERIAL PRIMARY KEY,
    package_name VARCHAR(255) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    unlocked_via_pin BOOLEAN DEFAULT FALSE
);

-- Pre-seed default settings and Instagram
INSERT INTO app_settings (device_id, master_blocker_active, require_pin_to_unlock)
VALUES ('default_device', TRUE, FALSE)
ON CONFLICT (device_id) DO NOTHING;

INSERT INTO blocked_apps (package_name, app_name, is_blocked)
VALUES ('com.instagram.android', 'Instagram', TRUE)
ON CONFLICT (package_name) DO NOTHING;
