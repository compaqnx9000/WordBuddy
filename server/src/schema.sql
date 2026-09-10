CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    last_login_at TIMESTAMPTZ,
    last_login_method TEXT,
    password_changed_at TIMESTAMPTZ,
    login_count INTEGER NOT NULL DEFAULT 0,
    avatar_url TEXT,
    last_device_label TEXT,
    last_device_platform TEXT,
    last_ip TEXT,
    last_ip_location TEXT,
    user_level INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sms_codes (
    id BIGSERIAL PRIMARY KEY,
    phone TEXT NOT NULL,
    code TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS sms_codes_phone_created
    ON sms_codes (phone, created_at DESC);

CREATE TABLE IF NOT EXISTS notebooks (
    id BIGSERIAL PRIMARY KEY,
    kind TEXT NOT NULL CHECK (kind IN ('catalog', 'user')),
    slug TEXT UNIQUE,
    owner_user_id BIGINT REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT notebooks_kind_shape CHECK (
        (kind = 'catalog' AND slug IS NOT NULL AND owner_user_id IS NULL)
        OR
        (kind = 'user' AND slug IS NULL AND owner_user_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS words (
    id BIGSERIAL PRIMARY KEY,
    notebook_id BIGINT NOT NULL REFERENCES notebooks (id) ON DELETE CASCADE,
    word TEXT NOT NULL,
    is_phrase BOOLEAN NOT NULL DEFAULT FALSE,
    ipa_uk TEXT,
    ipa_us TEXT,
    definitions JSONB NOT NULL DEFAULT '[]'::jsonb,
    examples JSONB NOT NULL DEFAULT '[]'::jsonb,
    near_words JSONB NOT NULL DEFAULT '[]'::jsonb,
    synonyms JSONB NOT NULL DEFAULT '[]'::jsonb,
    antonyms JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS words_notebook_word
    ON words (notebook_id, lower(word));

CREATE INDEX IF NOT EXISTS words_notebook_sort
    ON words (notebook_id, sort_order, id);

CREATE TABLE IF NOT EXISTS login_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    phone TEXT,
    method TEXT NOT NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    ip TEXT,
    user_agent TEXT,
    device_platform TEXT,
    device_brand TEXT,
    device_model TEXT,
    device_label TEXT,
    ip_location TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS login_events_created
    ON login_events (created_at DESC);

CREATE INDEX IF NOT EXISTS login_events_user
    ON login_events (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_key TEXT NOT NULL,
    platform TEXT,
    brand TEXT,
    model TEXT,
    label TEXT NOT NULL,
    os_version TEXT,
    app_version TEXT,
    last_ip TEXT,
    last_ip_location TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    login_count INTEGER NOT NULL DEFAULT 0,
    UNIQUE (user_id, device_key)
);

CREATE INDEX IF NOT EXISTS user_devices_user_seen
    ON user_devices (user_id, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS password_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users (id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    ip TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS admins (
    id BIGSERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS admin_audit (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT REFERENCES admins (id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    target_type TEXT,
    target_id TEXT,
    detail JSONB,
    ip TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS admin_audit_created
    ON admin_audit (created_at DESC);
