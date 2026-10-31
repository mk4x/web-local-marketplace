CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_versions (
    user_id uuid NOT NULL REFERENCES users (id),
    version bigint NOT NULL CHECK (version >= 1),
    prev_version bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    email text NOT NULL,
    display_name text NOT NULL,
    phone text,
    bio text,
    avatar_url text,
    locale text NOT NULL DEFAULT 'da-DK',
    home_location point,
    is_deleted boolean NOT NULL DEFAULT false,
    PRIMARY KEY (user_id, version),
    FOREIGN KEY (user_id, prev_version)
    REFERENCES user_versions (user_id, version),
    -- Prevents two writers from both creating the next version
    UNIQUE (user_id, prev_version),
    CHECK (
        (version = 1 AND prev_version IS NULL)
        OR (version > 1 AND prev_version = version - 1)
    ),
    CHECK (email = lower(email) AND position('@' IN email) > 1),
    CHECK (length(display_name) BETWEEN 1 AND 100)
);

CREATE INDEX user_versions_email_idx
ON user_versions (email);

CREATE VIEW current_users AS
SELECT DISTINCT ON (user_id)
    *
FROM user_versions
ORDER BY
    user_id,
    version DESC;
