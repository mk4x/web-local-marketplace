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
    title text NOT NULL,
    description text NOT NULL DEFAULT '',
    category text NOT NULL,
    tags text[] NOT NULL DEFAULT '{}',
    attributes jsonb NOT NULL DEFAULT '{}',
    image_urls text[] NOT NULL DEFAULT '{}',
    kind listing_kind NOT NULL DEFAULT 'sale',
    status listing_status NOT NULL DEFAULT 'draft',
    condition listing_condition,
    price numeric,
    currency text,
    price_negotiable boolean NOT NULL DEFAULT false,
    quantity integer NOT NULL DEFAULT 1,
    delivery delivery_option[] NOT NULL DEFAULT '{pickup}',
    location point,
    location_label text,
    pickup_radius_m integer,
    visible_during tstzrange NOT NULL DEFAULT tstzrange(now(), NULL),
    is_deleted boolean NOT NULL DEFAULT false,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', title), 'A')
        || setweight(to_tsvector('simple', description), 'B')
    ) STORED,
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
    -- numeric sorts NaN above Infinity, so this rejects both
    CHECK (
        price >= 0
        AND price < 'Infinity'
    ),
    CHECK (length(currency) BETWEEN 1 AND 16),
    CHECK ((price IS NULL) = (currency IS NULL)),
    CHECK (kind <> 'free' OR price IS NULL),
    CHECK (quantity >= 0),
    CHECK (pickup_radius_m > 0)
);

CREATE INDEX listing_versions_search_idx
ON listing_versions
USING gin (search_vector);

CREATE INDEX listing_versions_tags_idx
ON listing_versions
USING gin (tags);

CREATE INDEX listing_versions_attributes_idx
ON listing_versions
USING gin (attributes jsonb_path_ops);

CREATE INDEX listing_versions_location_idx
ON listing_versions
USING gist (location);

CREATE INDEX listing_versions_category_idx
ON listing_versions (
    category,
    status
);

CREATE VIEW current_listings AS
SELECT DISTINCT ON (lv.listing_id)
    lv.*,
    l.seller_id
FROM listing_versions AS lv
INNER JOIN listings AS l
    ON l.id = lv.listing_id
ORDER BY
    lv.listing_id,
    lv.version DESC;
