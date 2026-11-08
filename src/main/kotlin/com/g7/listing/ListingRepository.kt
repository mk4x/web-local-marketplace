package com.g7.listing

import com.g7.database.jooq.Tables.LISTINGS
import com.g7.database.jooq.Tables.LISTINGS_CURRENT
import com.g7.database.jooq.Tables.LISTING_VERSIONS
import com.g7.database.jooq.enums.DeliveryOption
import com.g7.database.jooq.enums.ListingCondition
import com.g7.database.jooq.enums.ListingKind
import com.g7.database.jooq.enums.ListingStatus
import com.g7.db.arg
import com.g7.db.column
import com.g7.db.fetchRows
import com.g7.db.read
import com.g7.db.writeRows
import org.jooq.DSLContext
import org.jooq.Record
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class NewListing(
    val sellerId: UUID,
    val title: String,
    val category: String,
    val lon: Double,
    val lat: Double,
    val city: String,
    val price: BigDecimal? = null,
    val currency: String = "dkk",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val kind: ListingKind = ListingKind.sale,
    val condition: ListingCondition? = null,
    val negotiable: Boolean = false,
    val quantity: Int = 1,
    val delivery: List<DeliveryOption> = listOf(DeliveryOption.pickup),
    val postalCode: String? = null,
    val country: String = "dk",
    val visibleDays: Int = 30,
    val id: UUID = UUID.randomUUID(),
)

data class ListingEdit(
    val listingId: UUID,
    val sellerId: UUID,
    val expectedVersion: Long,
    val reason: String,
    val title: String? = null,
    val description: String? = null,
    val price: BigDecimal? = null,
    val quantity: Int? = null,
    val negotiable: Boolean? = null,
    val status: ListingStatus? = null,
)

data class SearchFilter(
    val lon: Double,
    val lat: Double,
    val text: String? = null,
    val category: String? = null,
    val kind: ListingKind? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val radiusM: Int? = null,
    val limit: Int = 30,
    val offset: Int = 0,
)

data class Listing(
    val id: UUID,
    val sellerId: UUID,
    val sellerName: String,
    val version: Long,
    val updatedAt: OffsetDateTime,
    val title: String,
    val description: String,
    val category: String,
    val tags: List<String>,
    val kind: ListingKind,
    val status: ListingStatus,
    val condition: ListingCondition?,
    val price: BigDecimal?,
    val currency: String?,
    val negotiable: Boolean,
    val quantity: Int,
    val delivery: List<String>,
    val lon: Double?,
    val lat: Double?,
)

data class ListingHit(
    val id: UUID,
    val title: String,
    val price: BigDecimal?,
    val currency: String?,
    val thumbnail: String?,
    val distanceM: Double,
)

data class ListingRevision(
    val version: Long,
    val createdAt: OffsetDateTime,
    val reason: String?,
    val status: ListingStatus,
    val title: String,
    val price: BigDecimal?,
)

fun DSLContext.createListings(listings: List<NewListing>) =
    writeRows(INSERT, listings.map { it.toRow() })

fun DSLContext.editListings(edits: List<ListingEdit>) =
    writeRows(EDIT, edits.map { it.toRow() })

fun DSLContext.findListings(ids: List<UUID>): List<Listing> =
    fetchRows(SELECT, mapOf("ids" to ids)).map { it.toListing() }

fun DSLContext.searchListings(filter: SearchFilter): List<ListingHit> =
    fetchRows(SEARCH, filter.toRow()).map { it.toListingHit() }

fun DSLContext.listingHistory(id: UUID): List<ListingRevision> =
    fetchRows(HISTORY, mapOf("id" to id)).map { it.toListingRevision() }

private const val INSERT = """
with r as (
    select * from jsonb_to_recordset(cast(? as jsonb)) as r(
        id uuid,
        seller_id uuid,
        title text,
        description text,
        category category_path,
        tags text[],
        kind listing_kind,
        condition listing_condition,
        price numeric,
        currency text,
        price_negotiable boolean,
        quantity integer,
        delivery delivery_option[],
        lon float8,
        lat float8,
        city text,
        postal_code text,
        country text,
        visible_days integer
    )
), l as (
    insert into listings (id, seller_id)
    select id, seller_id from r
), v as (
    insert into listing_versions (
        listing_id,
        version,
        created_by,
        title,
        description,
        category,
        tags,
        kind,
        status,
        condition,
        price,
        price_negotiable,
        quantity,
        delivery,
        location,
        pickup_address,
        visible_during
    )
    select
        r.id,
        1,
        r.seller_id,
        r.title,
        r.description,
        r.category,
        cast(r.tags as tag_list),
        r.kind,
        'active',
        r.condition,
        case when r.price is null
            then null
            else cast(row(r.price, lower(r.currency)) as price)
        end,
        r.price_negotiable,
        r.quantity,
        r.delivery,
        cast(st_setsrid(st_makepoint(r.lon, r.lat), 4326) as geography),
        cast(row(null, r.postal_code, r.city, r.country) as postal_address),
        tstzrange(now(), now() + make_interval(days => r.visible_days))
    from r
    returning *
)
insert into listings_current (
    listing_id,
    seller_id,
    version,
    updated_at,
    title,
    description,
    category,
    tags,
    attributes,
    image_urls,
    kind,
    status,
    condition,
    price,
    budget,
    price_negotiable,
    quantity,
    delivery,
    location,
    pickup_address,
    pickup_radius,
    visible_during,
    pickup_windows,
    relist_every,
    is_deleted
)
select
    listing_id,
    created_by,
    version,
    created_at,
    title,
    description,
    category,
    tags,
    attributes,
    image_urls,
    kind,
    status,
    condition,
    price,
    budget,
    price_negotiable,
    quantity,
    delivery,
    location,
    pickup_address,
    pickup_radius,
    visible_during,
    pickup_windows,
    relist_every,
    is_deleted
from v
"""

private const val EDIT = """
with r as (
    select * from jsonb_to_recordset(cast(? as jsonb)) as r(
        listing_id uuid,
        seller_id uuid,
        version bigint,
        change_reason text,
        title text,
        description text,
        price numeric,
        quantity integer,
        price_negotiable boolean,
        status listing_status
    )
), cur as (
    select c.*
    from listings_current c
    join r on
        r.listing_id = c.listing_id
        and r.version = c.version
        and r.seller_id = c.seller_id
    for update of c
), v as (
    insert into listing_versions (
        listing_id,
        version,
        prev_version,
        created_by,
        change_reason,
        title,
        description,
        category,
        tags,
        attributes,
        image_urls,
        kind,
        status,
        condition,
        price,
        budget,
        price_negotiable,
        quantity,
        delivery,
        location,
        pickup_address,
        pickup_radius,
        visible_during,
        pickup_windows,
        relist_every,
        is_deleted
    )
    select
        cur.listing_id,
        cur.version + 1,
        cur.version,
        cur.seller_id,
        r.change_reason,
        coalesce(r.title, cur.title),
        coalesce(r.description, cur.description),
        cur.category,
        cur.tags,
        cur.attributes,
        cur.image_urls,
        cur.kind,
        coalesce(r.status, cur.status),
        cur.condition,
        case when r.price is null
            then cur.price
            else cast(row(r.price, (cur.price).currency) as price)
        end,
        cur.budget,
        coalesce(r.price_negotiable, cur.price_negotiable),
        coalesce(r.quantity, cur.quantity),
        cur.delivery,
        cur.location,
        cur.pickup_address,
        cur.pickup_radius,
        cur.visible_during,
        cur.pickup_windows,
        cur.relist_every,
        coalesce(r.status, cur.status) = 'removed'
    from cur
    join r on r.listing_id = cur.listing_id
    returning *
)
update listings_current c
set version = v.version,
    updated_at = v.created_at,
    title = v.title,
    description = v.description,
    status = v.status,
    price = v.price,
    price_negotiable = v.price_negotiable,
    quantity = v.quantity,
    is_deleted = v.is_deleted
from v
where c.listing_id = v.listing_id
"""

private const val SELECT = """
with p as (
    select * from jsonb_to_record(cast(? as jsonb)) as p(ids uuid[])
)
select
    l.listing_id,
    l.seller_id,
    u.display_name as seller_name,
    l.version,
    l.updated_at,
    l.title,
    l.description,
    cast(l.category as text) as category,
    cast(l.tags as text[]) as tags,
    cast(l.kind as text) as kind,
    cast(l.status as text) as status,
    cast(l.condition as text) as condition,
    (l.price).amount as price_amount,
    (l.price).currency as price_currency,
    l.price_negotiable, l.quantity,
    cast(l.delivery as text[]) as delivery,
    st_x(cast(l.location as geometry)) as lon,
    st_y(cast(l.location as geometry)) as lat
from current_listings l
join current_users u on u.user_id = l.seller_id
where l.listing_id in (select unnest(ids) from p)
"""

private const val SEARCH = """
with p as (
    select * from jsonb_to_record(cast(? as jsonb)) as p(
        lon float8,
        lat float8,
        q text,
        category category_path,
        kind listing_kind,
        min_price numeric,
        max_price numeric,
        radius_m integer,
        page_size integer,
        page_offset integer
    )
), origin as (
    select
        cast(st_setsrid(st_makepoint(lon, lat), 4326) as geography) as pt,
        websearch_to_tsquery('simple', q) as tsq
    from p
)
select
    l.listing_id,
    l.title,
    (l.price).amount as price_amount,
    (l.price).currency as price_currency,
    cast(l.image_urls[1] as text) as thumbnail,
    st_distance(l.location, o.pt) as distance_m
from current_listings l, p, origin o
where
    l.status = 'active'
    and l.visible_during @> now()
    and (o.tsq is null or l.search_vector @@ o.tsq)
    and (p.category is null or l.category <@ p.category)
    and (p.kind is null or l.kind = p.kind)
    and (p.min_price is null or (l.price).amount >= p.min_price)
    and (p.max_price is null or (l.price).amount <= p.max_price)
    and (p.radius_m is null or st_dwithin(l.location, o.pt, p.radius_m))
order by ts_rank(l.search_vector, o.tsq) desc, l.location <-> o.pt
limit (select page_size from p) offset (select page_offset from p)
"""

private const val HISTORY = """
with p as (
    select * from jsonb_to_record(cast(? as jsonb)) as p(id uuid)
)
select
    version,
    created_at,
    change_reason,
    cast(status as text) as status,
    title,
    (price).amount as price_amount
from listing_versions
where listing_id = (select id from p)
order by version desc
"""

private val CATEGORY = column("category", String::class.java)
private val TAGS = column("tags", Array<String>::class.java)
private val DELIVERY = column("delivery", Array<String>::class.java)
private val SELLER_NAME = column("seller_name", String::class.java)
private val PRICE = column("price_amount", BigDecimal::class.java)
private val CURRENCY = column("price_currency", String::class.java)
private val THUMBNAIL = column("thumbnail", String::class.java)
private val DISTANCE_M = column("distance_m", Double::class.javaObjectType)
private val LON = column("lon", Double::class.javaObjectType)
private val LAT = column("lat", Double::class.javaObjectType)

private fun NewListing.toRow() =
    mapOf(
        LISTINGS.ID.arg(id),
        LISTINGS.SELLER_ID.arg(sellerId),
        LISTING_VERSIONS.TITLE.arg(title),
        LISTING_VERSIONS.DESCRIPTION.arg(description),
        LISTING_VERSIONS.CATEGORY.arg(category),
        LISTING_VERSIONS.KIND.arg(kind),
        LISTING_VERSIONS.CONDITION.arg(condition),
        LISTING_VERSIONS.PRICE_NEGOTIABLE.arg(negotiable),
        LISTING_VERSIONS.QUANTITY.arg(quantity),
        "tags" to tags,
        "delivery" to delivery.map { it.literal },
        "price" to price,
        "currency" to currency,
        "lon" to lon,
        "lat" to lat,
        "city" to city,
        "postal_code" to postalCode,
        "country" to country,
        "visible_days" to visibleDays,
    )

private fun SearchFilter.toRow() =
    mapOf(
        "lon" to lon,
        "lat" to lat,
        "q" to text,
        "category" to category,
        "kind" to kind?.literal,
        "min_price" to minPrice,
        "max_price" to maxPrice,
        "radius_m" to radiusM,
        "page_size" to limit,
        "page_offset" to offset,
    )

private fun ListingEdit.toRow() =
    mapOf(
        LISTINGS_CURRENT.LISTING_ID.arg(listingId),
        LISTINGS_CURRENT.SELLER_ID.arg(sellerId),
        LISTINGS_CURRENT.VERSION.arg(expectedVersion),
        LISTING_VERSIONS.CHANGE_REASON.arg(reason),
        LISTINGS_CURRENT.TITLE.arg(title),
        LISTINGS_CURRENT.DESCRIPTION.arg(description),
        LISTINGS_CURRENT.PRICE_NEGOTIABLE.arg(negotiable),
        LISTINGS_CURRENT.QUANTITY.arg(quantity),
        LISTINGS_CURRENT.STATUS.arg(status),
        "price" to price,
    )

private fun Record.toListing() =
    Listing(
        id = read(LISTINGS_CURRENT.LISTING_ID),
        sellerId = read(LISTINGS_CURRENT.SELLER_ID),
        sellerName = read(SELLER_NAME),
        version = read(LISTINGS_CURRENT.VERSION),
        updatedAt = read(LISTINGS_CURRENT.UPDATED_AT),
        title = read(LISTINGS_CURRENT.TITLE),
        description = read(LISTINGS_CURRENT.DESCRIPTION),
        category = read(CATEGORY),
        tags = read(TAGS).toList(),
        kind = read(LISTINGS_CURRENT.KIND),
        status = read(LISTINGS_CURRENT.STATUS),
        condition = read(LISTINGS_CURRENT.CONDITION),
        price = read(PRICE),
        currency = read(CURRENCY),
        negotiable = read(LISTINGS_CURRENT.PRICE_NEGOTIABLE),
        quantity = read(LISTINGS_CURRENT.QUANTITY),
        delivery = read(DELIVERY).toList(),
        lon = read(LON),
        lat = read(LAT),
    )

private fun Record.toListingHit() =
    ListingHit(
        id = read(LISTINGS_CURRENT.LISTING_ID),
        title = read(LISTINGS_CURRENT.TITLE),
        price = read(PRICE),
        currency = read(CURRENCY),
        thumbnail = read(THUMBNAIL),
        distanceM = read(DISTANCE_M),
    )

private fun Record.toListingRevision() =
    ListingRevision(
        version = read(LISTING_VERSIONS.VERSION),
        createdAt = read(LISTING_VERSIONS.CREATED_AT),
        reason = read(LISTING_VERSIONS.CHANGE_REASON),
        status = read(LISTING_VERSIONS.STATUS),
        title = read(LISTING_VERSIONS.TITLE),
        price = read(PRICE),
    )
