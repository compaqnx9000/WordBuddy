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
    session_version INTEGER NOT NULL DEFAULT 0,
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

-- Shared 谐音助记 tips keyed by canonical word text (not notebook-local word id).
CREATE TABLE IF NOT EXISTS word_homophones (
    id BIGSERIAL PRIMARY KEY,
    word_key TEXT NOT NULL,
    body TEXT NOT NULL,
    author_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    like_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (word_key, author_user_id)
);

CREATE INDEX IF NOT EXISTS word_homophones_top
    ON word_homophones (word_key, like_count DESC, id DESC);

CREATE TABLE IF NOT EXISTS word_homophone_likes (
    homophone_id BIGINT NOT NULL REFERENCES word_homophones (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (homophone_id, user_id)
);

CREATE TABLE IF NOT EXISTS user_checkins (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_points INTEGER NOT NULL DEFAULT 0,
    streak_days INTEGER NOT NULL DEFAULT 0,
    last_checkin_date DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_checkin_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    checkin_date DATE NOT NULL,
    streak_days INTEGER NOT NULL,
    points_earned INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, checkin_date)
);

CREATE INDEX IF NOT EXISTS user_checkin_logs_date
    ON user_checkin_logs (checkin_date DESC);

CREATE INDEX IF NOT EXISTS user_checkin_logs_user
    ON user_checkin_logs (user_id, checkin_date DESC);

CREATE INDEX IF NOT EXISTS user_checkins_points
    ON user_checkins (total_points DESC);

CREATE TABLE IF NOT EXISTS points_ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reason TEXT NOT NULL,
    ref_type TEXT,
    ref_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS points_ledger_user
    ON points_ledger (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS gifts (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    subtitle TEXT,
    cover_emoji TEXT NOT NULL DEFAULT '🎁',
    cover_color TEXT NOT NULL DEFAULT '#1B6CA8',
    category TEXT NOT NULL DEFAULT 'recommend',
    points_cost INTEGER NOT NULL DEFAULT 0,
    cash_fen INTEGER NOT NULL DEFAULT 0,
    original_price_fen INTEGER,
    points_offset_fen INTEGER,
    stock INTEGER NOT NULL DEFAULT -1,
    redeemed_count INTEGER NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    need_address BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS gifts_published_sort
    ON gifts (published, sort_order ASC, id DESC);

CREATE TABLE IF NOT EXISTS gift_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    gift_id BIGINT REFERENCES gifts (id) ON DELETE SET NULL,
    gift_title TEXT NOT NULL,
    cover_emoji TEXT,
    cover_color TEXT,
    points_spent INTEGER NOT NULL DEFAULT 0,
    cash_fen INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'completed',
    address_name TEXT,
    address_phone TEXT,
    address_detail TEXT,
    remark TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS gift_orders_user
    ON gift_orders (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS gift_orders_created
    ON gift_orders (created_at DESC);
