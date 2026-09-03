CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone TEXT NOT NULL UNIQUE,
    password_hash TEXT,
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
