CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    current_version bigint NOT NULL,
    current_email text NOT NULL,
    current_is_deleted boolean NOT NULL DEFAULT false,
    FOREIGN KEY (id, current_version, current_email, current_is_deleted)
    REFERENCES user_versions (user_id, version, email, is_deleted)
    DEFERRABLE INITIALLY DEFERRED
);

CREATE UNIQUE INDEX users_current_email_key
ON users (current_email)
WHERE NOT current_is_deleted;

CREATE TABLE user_versions (
    user_id uuid NOT NULL REFERENCES users (id),
    version bigint NOT NULL CHECK (version >= 1),
    prev_version bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid REFERENCES users (id),
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
    UNIQUE (user_id, version, email, is_deleted),
    CHECK (
        (version = 1 AND prev_version IS NULL)
        OR (version > 1 AND prev_version = version - 1)
    ),
    CHECK (email = lower(email) AND position('@' IN email) > 1),
    CHECK (length(display_name) BETWEEN 1 AND 100),
    CHECK (length(phone) <= 32),
    CHECK (length(bio) <= 2000),
    CHECK (length(avatar_url) <= 2048),
    CHECK (length(locale) BETWEEN 2 AND 35),
);

CREATE VIEW current_users AS
SELECT
    u.id AS user_id,
    v.version,
    v.created_at AS updated_at,
    v.email,
    v.display_name,
    v.phone,
    v.bio,
    v.avatar_url,
    v.locale,
    v.home_location
FROM users AS u
INNER JOIN user_versions AS v
    ON v.user_id = u.id
    AND v.version = u.current_version
WHERE NOT u.current_is_deleted;

CREATE TYPE listing_kind AS ENUM (
    'sale',
    'free',
    'wanted',
    'trade'
);

CREATE TYPE listing_status AS ENUM (
    'draft',
    'active',
    'reserved',
    'sold',
    'expired',
    'removed'
);

CREATE TYPE listing_condition AS ENUM (
    'new',
    'like_new',
    'good',
    'fair',
    'poor'
);

CREATE TYPE delivery_option AS ENUM (
    'pickup',
    'local_delivery',
    'shipping'
);

CREATE TABLE categories (
    slug text PRIMARY KEY CHECK (slug ~ '^[a-z0-9_-]{1,64}$'),
    label text NOT NULL CHECK (length(label) BETWEEN 1 AND 100)
);

CREATE TABLE listings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX listings_seller_idx
ON listings (seller_id);

CREATE TABLE listing_versions (
    listing_id uuid NOT NULL REFERENCES listings (id),
    version bigint NOT NULL CHECK (version >= 1),
    prev_version bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid REFERENCES users (id),
    title text NOT NULL,
    description text NOT NULL DEFAULT '',
    category text NOT NULL REFERENCES categories (slug),
    tags text[] NOT NULL DEFAULT '{}',
    attributes jsonb NOT NULL DEFAULT '{}',
    image_urls text[] NOT NULL DEFAULT '{}',
    kind listing_kind NOT NULL DEFAULT 'sale',
    status listing_status NOT NULL DEFAULT 'draft',
    condition listing_condition,
    price numeric(14, 2),
    currency text,
    price_negotiable boolean NOT NULL DEFAULT false,
    quantity integer NOT NULL DEFAULT 1,
    delivery delivery_option[] NOT NULL DEFAULT '{pickup}',
    location point,
    location_label text,
    pickup_radius_m integer,
    visible_during tstzrange NOT NULL DEFAULT tstzrange(now(), NULL),
    is_deleted boolean NOT NULL DEFAULT false,
    PRIMARY KEY (listing_id, version),
    FOREIGN KEY (listing_id, prev_version)
    REFERENCES listing_versions (listing_id, version),
    -- Prevents two writers from both creating the next version
    UNIQUE (listing_id, prev_version),
    CHECK (
        (version = 1 AND prev_version IS NULL)
        OR (version > 1 AND prev_version = version - 1)
    ),
    CHECK (length(title) BETWEEN 3 AND 150),
    CHECK (
        price >= 0
        AND price < 'Infinity'
    ),
    CHECK (currency ~ '^[A-Z]{3}$'),
    CHECK ((price IS NULL) = (currency IS NULL)),
    CHECK (kind <> 'free' OR price IS NULL),
    CHECK (quantity >= 0),
    CHECK (status <> 'active' OR quantity > 0),
    CHECK (pickup_radius_m > 0),
    CHECK (pickup_radius_m IS NULL OR location IS NOT NULL),
    CHECK (cardinality(delivery) >= 1),
    CHECK (length(description) <= 10000),
    CHECK (length(location_label) <= 200),
    CHECK (cardinality(tags) <= 20),
    CHECK (cardinality(image_urls) <= 20),
    CHECK (jsonb_typeof(attributes) = 'object'),
    CHECK (NOT isempty(visible_during) AND lower(visible_during) IS NOT NULL),
    CHECK (NOT is_deleted OR status = 'removed')
);

CREATE TABLE listings_current (
    listing_id uuid PRIMARY KEY REFERENCES listings (id),
    seller_id uuid NOT NULL REFERENCES users (id),
    version bigint NOT NULL,
    updated_at timestamptz NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    category text NOT NULL REFERENCES categories (slug),
    tags text[] NOT NULL,
    attributes jsonb NOT NULL,
    image_urls text[] NOT NULL,
    kind listing_kind NOT NULL,
    status listing_status NOT NULL,
    condition listing_condition,
    price numeric(14, 2),
    currency text,
    price_negotiable boolean NOT NULL,
    quantity integer NOT NULL,
    delivery delivery_option[] NOT NULL,
    location point,
    location_label text,
    pickup_radius_m integer,
    visible_during tstzrange NOT NULL,
    is_deleted boolean NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', title), 'A')
        || setweight(to_tsvector('simple', description), 'B')
    ) STORED,
    -- Guarantees the current row always points at a real version row.
    FOREIGN KEY (listing_id, version)
    REFERENCES listing_versions (listing_id, version)
);

CREATE INDEX listings_current_search_idx
ON listings_current
USING gin (search_vector);

CREATE INDEX listings_current_tags_idx
ON listings_current
USING gin (tags);

CREATE INDEX listings_current_attributes_idx
ON listings_current
USING gin (attributes jsonb_path_ops);

CREATE INDEX listings_current_location_idx
ON listings_current
USING gist (location);

CREATE INDEX listings_current_category_idx
ON listings_current (
    category,
    status
);

CREATE INDEX listings_current_seller_idx
ON listings_current (seller_id, status);

CREATE INDEX listings_current_visible_idx
ON listings_current
USING gist (visible_during);

CREATE VIEW current_listings AS
SELECT *
FROM listings_current
WHERE NOT is_deleted;
