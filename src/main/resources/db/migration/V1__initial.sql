create extension if not exists citext;
create extension if not exists ltree;
create extension if not exists pg_trgm;
create extension if not exists btree_gist;
create extension if not exists postgis;

-- enums
create type listing_kind as enum (
    'sale',
    'free',
    'wanted',
    'trade'
);

create type listing_status as enum (
    'draft',
    'active',
    'reserved',
    'sold',
    'expired',
    'removed'
);

create type listing_condition as enum (
    'new',
    'like_new',
    'good',
    'fair',
    'poor'
);

create type delivery_option as enum (
    'pickup',
    'local_delivery',
    'shipping'
);

-- domain
create domain version_no as bigint
check (value >= 1);

-- citext makes comparison, uniqueness and indexes case-insensitive, so no
-- lower() checks are needed. Requires a dot in the host part.
create domain email_address as citext
check (
    length(value) <= 254
    and value ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'
);

create domain display_name as text
check (
    value = btrim(value)
    and length(value) between 1 and 100
);

-- international format
create domain phone_e164 as text
check (value ~ '^\+[1-9][0-9]{6,14}$');

create domain http_url as text
check (
    length(value) <= 2048
    and value ~* '^https?://[^[:space:]]+$'
);

create domain locale_tag as text
check (value ~ '^[a-z]{2,3}(-[a-zA-Z0-9]{2,8})*$');
 
create domain bio_text as text
check (length(value) <= 2000);
 
create domain change_reason as text
check (length(value) between 1 and 500);
 
create domain sha256_digest as bytea
check (length(value) = 32);
 
-- ISO 4217
create domain currency_code as text
check (value ~ '^[a-z]{3}$');
 
-- ISO 3166-1 alpha-2
create domain country_code as text
check (value ~ '^[a-z]{2}$');
 
create domain listing_title as text
 check (
    value = btrim(value)
    and length(value) between 3 and 150
);

create domain listing_description as text
check (length(value) <= 10000);
 
create domain stock_quantity as integer
check (value >= 0);
 
create domain distance_m as integer
check (value between 1 and 500000);
 
create domain relist_interval as interval
check (
    value between interval '1 day'
    and interval '90 days'
);
 
create domain attributes_doc as jsonb
check (jsonb_typeof(value) = 'object');
 
create domain category_path as ltree
check (nlevel(value) between 1 and 6);
 
create domain tag as citext
check (length(value) between 1 and 40);
 
create domain tag_list as tag[]
check (cardinality(value) <= 20);
 
create domain url_list as http_url[]
check (cardinality(value) <= 20);

-- composite types
-- A price is an amount and a currency, never one without the other.
create type price_t as (
    amount numeric,
    currency currency_code
);

-- The domain adds the rules. NULL (no price) is allowed; a partly filled
-- price is not, because IS NOT NULL on a composite means every field is
-- set. Amount: not negative, at most 2 decimals, below 10^12, not
-- NaN/Infinity (NaN sorts above Infinity, so one comparison covers both)
create domain price as price_t
check (
    value is null
    or (
        value is not null
        and (value).amount >= 0
        and (value).amount < 1000000000000
        and scale((value).amount) <= 2
    )
);

-- Custom range type: a span of amounts, e.g. [100, 500).
create type amount_range as range (
    subtype = numeric,
    multirange_type_name = amount_multirange
);

-- What a buyer is willing to pay on a `wanted` listing.
create type budget_t as (
    amount amount_range,
    currency currency_code
);

create domain budget as budget_t
check (
    value is null
    or (
        value is not null
        and not isempty((value).amount)
        and coalesce(lower((value).amount), 0) >= 0
        and scale(coalesce(lower((value).amount), 0)) <= 2
        and scale(coalesce(upper((value).amount), 0)) <= 2
    )
);
 
create type postal_address_t as (
    street text,
    postal_code text,
    city text,
    country country_code
);

-- City and country are required; street and postal code are optional.
create domain postal_address as postal_address_t
check (
    value is null
    or (
        (value).city is not null
        and (value).country is not null
        and length((value).street) <= 200
        and length((value).postal_code) <= 16
        and length((value).city) <= 100
    )
);

-- users

-- The foreign key to user_versions is added after that table exists
-- (the two tables reference each other).
create table users (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    current_version version_no not null,
    current_email email_address not null,
    current_is_deleted boolean not null default false
);

create unique index users_current_email_key
on users (current_email)
where not current_is_deleted;

create table user_versions (
    user_id uuid not null references users (id),
    version version_no not null check (version >= 1),
    prev_version version_no,
    created_at timestamptz not null default now(),
    created_by uuid references users (id),
    email email_address not null,
    display_name display_name not null,
    phone phone_e164,
    bio bio_text,
    avatar_url http_url,
    locale locale_tag not null default 'da-dk',
    home_location geography(point, 4326),
    is_deleted boolean not null default false,
    primary key (user_id, version),
    foreign key (user_id, prev_version)
    references user_versions (user_id, version),
    -- prevents two writers from both creating the next version
    unique (user_id,  prev_version),
    unique (user_id, version, email, is_deleted),
    check (
        (version = 1 and prev_version is null)
        or (version > 1 and prev_version = version - 1)
    )
);

alter table users
add foreign key (id, current_version, current_email, current_is_deleted)
references user_versions (user_id, version, email, is_deleted)
-- Deferred so a user and its first version go in one transaction.
deferrable initially deferred;

create view current_users as
select
    u.id as user_id,
    v.version,
    v.created_at as updated_at,
    v.email,
    v.display_name,
    v.phone,
    v.bio,
    v.avatar_url,
    v.locale,
    v.home_location
from users as u
inner join user_versions as v
    on v.user_id = u.id
    and v.version = u.current_version
where not u.current_is_deleted;

-- listings

create table categories (
    path category_path primary key,
    parent category_path generated always as (
        case when nlevel(path) > 1 then subpath(path, 0, -1) end
    ) stored references categories (path),
    label display_name not null
);

create index categories_path_gist_idx
on categories
using gist (path);

create table listings (
    id uuid primary key default gen_random_uuid(),
    seller_id uuid not null references users (id),
    created_at timestamptz not null default now()
);

create index listings_seller_idx
on listings (seller_id);

create table listing_versions (
    listing_id uuid not null references listings (id),
    version version_no not null,
    prev_version version_no,
    created_at timestamptz not null default now(),
    created_by uuid references users (id),
    created_from inet,
    change_reason change_reason,
    title listing_title not null,
    description listing_description not null default '',
    category category_path not null references categories (path),
    tags tag_list not null default '{}',
    attributes attributes_doc not null default '{}',
    image_urls url_list not null default '{}',
    kind listing_kind not null default 'sale',
    status listing_status not null default 'draft',
    condition listing_condition,
    -- sellers set a price; buyers on 'wanted' listings set a budget.
    price price,
    budget budget,
    price_negotiable boolean not null default false,
    quantity stock_quantity not null default 1,
    delivery delivery_option[] not null default '{pickup}',
    location geography(point, 4326),
    pickup_address postal_address,
    pickup_radius distance_m,
    -- when the listing is shown: one range.
    visible_during tstzrange not null default tstzrange(now(), null),
    -- when the seller can hand the item over: several ranges at once.
    pickup_windows tstzmultirange,
    relist_every relist_interval,
    is_deleted boolean not null default false,
    primary key (listing_id, version),
    foreign key (listing_id, prev_version)
    references listing_versions (listing_id, version),
    -- prevents two writers from both creating the next version
    unique (listing_id, prev_version),
    check (
        (version = 1 and prev_version is null)
        or (version > 1 and prev_version = version - 1)
    ),
    check (kind = 'wanted' or budget is null),
    check (kind <> 'wanted' or price is not null),
    check (kind <> 'free' or (price is null and budget is null)),
    check (status <> 'active' or quantity > 0),
    check (pickup_radius is null or location is not null),
    check (cardinality(delivery) >= 1),
    check (not isempty(visible_during) and not lower_inf(visible_during)),
    check (pickup_windows is null or not isempty(pickup_windows)),
    check (not is_deleted or status = 'removed')
);
 
-- Current state, one row per listing. This is what you search. The
-- application writes it in the same statement as the new version.
create table listings_current (
    listing_id uuid primary key references listings (id),
    seller_id uuid not null references users (id),
    version version_no not null,
    updated_at timestamptz not null,
    title listing_title not null,
    description listing_description not null,
    category category_path not null references categories (path),
    tags tag_list not null,
    attributes attributes_doc not null,
    image_urls url_list not null,
    kind listing_kind not null,
    status listing_status not null,
    condition listing_condition,
    price price,
    budget budget,
    price_negotiable boolean not null,
    quantity stock_quantity not null,
    delivery delivery_option[] not null default '{pickup}',
    location geography(point, 4326),
    pickup_address postal_address,
    pickup_radius distance_m,
    visible_during tstzrange not null,
    pickup_windows tstzmultirange,
    relist_every relist_interval,
    is_deleted boolean not null,
    search_vector tsvector generated always as (
        setweight(to_tsvector('simple', title), 'a')
        || setweight(to_tsvector('simple', description), 'b')
    ) stored,
    -- guarantees the current row points at a real version row.
    foreign key (listing_id, version)
    references listing_versions (listing_id, version)
);
 
-- Full-text search.
create index listings_current_search_idx
on listings_current
using gin (search_vector);
 
-- Fuzzy title search and typo tolerance: title % 'bycicle'.
create index listings_current_title_trgm_idx
on listings_current
using gin (title gin_trgm_ops);
 
create index listings_current_tags_idx
on listings_current
using gin (tags);
 
create index listings_current_attributes_idx
on listings_current
using gin (attributes jsonb_path_ops);
 
-- Subtree browsing: category <@ 'vehicles'.
create index listings_current_category_idx
on listings_current
using gist (category);
 
-- Radius and nearest-first queries on real distances:
--   ST_DWithin(location, point, metres)
--   ORDER BY location <-> point
-- The leading status column (via btree_gist) lets one index serve both
-- the status filter and the spatial search.
create index listings_current_geo_idx
on listings_current
using gist (status, location);
 
create index listings_current_visible_idx
on listings_current
using gist (visible_during);
 
create index listings_current_seller_idx
on listings_current (seller_id, status);
 
-- Sort/filter by the amount inside the composite price.
create index listings_current_price_idx
on listings_current (((price).amount))
where price is not null;
 
create view current_listings as
select *
from listings_current
where not is_deleted;
