package com.g7.listing

import com.g7.database.jooq.Tables.LISTINGS
import com.g7.database.jooq.Tables.LISTINGS_CURRENT
import com.g7.database.jooq.Tables.LISTING_VERSIONS
import com.g7.database.jooq.enums.DeliveryOption
import com.g7.database.jooq.enums.ListingCondition
import com.g7.database.jooq.enums.ListingKind
import com.g7.database.jooq.enums.ListingStatus
import com.g7.db.arg
import com.g7.db.batchRows
import com.g7.db.column
import com.g7.db.fetchRows
import com.g7.db.read
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
    batchRows(INSERT, listings.map { it.toRow() })

fun DSLContext.editListings(edits: List<ListingEdit>) =
    batchRows(EDIT, edits.map { it.toRow() })

fun DSLContext.findListings(ids: List<UUID>): List<Listing> =
    fetchRows(SELECT, "ids" to ids.toTypedArray())
        .map { it.toListing() }

fun DSLContext.searchListings(f: SearchFilter): List<ListingHit> =
    fetchRows(
        SEARCH,
        "lon" to f.lon,
        "lat" to f.lat,
        "q" to f.text,
        "category" to f.category,
        "kind" to f.kind?.literal,
        "min_price" to f.minPrice,
        "max_price" to f.maxPrice,
        "radius_m" to f.radiusM,
        "limit" to f.limit,
        "offset" to f.offset,
    ).map { it.toListingHit() }

fun DSLContext.listingHistory(id: UUID): List<ListingRevision> =
    fetchRows(HISTORY, "id" to id)
        .map { it.toListingRevision() }

private const val INSERT = """
with l as (
    insert into listings (id, seller_id) values (:id, :seller_id)
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
    values (
        :id,
        1,
        :seller_id,
        :title,
        :description,
        cast(:category as category_path),
        cast(string_to_array(:tags, ',') as tag_list),
        cast(:kind as listing_kind),
        'active',
        cast(:condition as listing_condition),
        case when cast(:price as numeric) is null
             then null
             else cast(row(:price, lower(:currency)) as price)
        end,
        :price_negotiable, :quantity,
        cast(string_to_array(:delivery, ',') as delivery_option[]),
        cast(st_setsrid(st_makepoint(:lon, :lat), 4326) as geography),
        cast(row(null, :postal_code, :city, :country) as postal_address),
        tstzrange(now(), now() + make_interval(days => :visible_days))
    )
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
with cur as (
    select *
    from listings_current
    where listing_id = :listing_id
      and version = :version
      and seller_id = :seller_id
    for update
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
        listing_id,
        version + 1,
        version,
        seller_id,
        :change_reason,
        coalesce(:title, title),
        coalesce(:description, description),
        category,
        tags,
        attributes,
        image_urls,
        kind,
        coalesce(cast(:status as listing_status), status),
        condition,
        case when cast(:price as numeric) is null
            then price
            else cast(row(:price, (price).currency) as price)
        end,
        budget,
        coalesce(:price_negotiable, price_negotiable),
        coalesce(:quantity, quantity),
        delivery,
        location,
        pickup_address,
        pickup_radius,
        visible_during,
        pickup_windows,
        relist_every,
        coalesce(cast(:status as listing_status),
        status
    ) = 'removed'
    from cur
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
       l.price_negotiable,
        l.quantity,
       cast(l.delivery as text[]) as delivery,
       st_x(cast(l.location as geometry)) as lon,
       st_y(cast(l.location as geometry)) as lat
from current_listings l
join current_users u on u.user_id = l.seller_id
where l.listing_id = any(:ids)
"""

private const val SEARCH = """
select
    l.listing_id,
    l.title,
    (l.price).amount as price_amount,
    (l.price).currency as price_currency,
    cast(l.image_urls[1] as text) as thumbnail,
    st_distance(l.location, p.pt) as distance_m
from current_listings l
cross join (
    select cast(st_setsrid(st_makepoint(:lon, :lat), 4326) as geography) as pt
) p
cross join (select websearch_to_tsquery('simple', :q) as tsq) q
where l.status = 'active'
  and l.visible_during @> now()
  and (
      q.tsq is null
      or l.search_vector @@ q.tsq
  ) and (
      cast(:category as ltree) is null
      or l.category <@ cast(:category as ltree)
  )
  and (
      cast(:kind as text) is null
      or l.kind = cast(:kind as listing_kind)
  ) and (
      cast(:min_price as numeric) is null
      or (l.price).amount >= :min_price
  ) and (
      cast(:max_price as numeric) is null
      or (l.price).amount <= :max_price
  )
  and (
      cast(:radius_m as integer) is null
      or st_dwithin(l.location, p.pt, :radius_m)
  )
order by ts_rank(l.search_vector, q.tsq) desc, l.location <-> p.pt
limit :limit offset :offset
"""

private const val HISTORY = """
select
    version,
    created_at,
    change_reason,
    cast(status as text) as status,
    title,
    (price).amount as price_amount
from listing_versions
where listing_id = :id
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
        "tags" to tags.joinToString(","),
        "delivery" to delivery.joinToString(",") { it.literal },
        "price" to price,
        "currency" to currency,
        "lon" to lon,
        "lat" to lat,
        "city" to city,
        "postal_code" to postalCode,
        "country" to country,
        "visible_days" to visibleDays,
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
