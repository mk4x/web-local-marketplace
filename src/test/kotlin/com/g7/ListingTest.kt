package com.g7

import com.g7.database.jooq.enums.DeliveryOption
import com.g7.database.jooq.enums.ListingCondition
import com.g7.database.jooq.enums.ListingKind
import com.g7.database.jooq.enums.ListingStatus
import com.g7.db.StaleVersionException
import com.g7.listing.ListingEdit
import com.g7.listing.NewListing
import com.g7.listing.SearchFilter
import com.g7.listing.createListings
import com.g7.listing.editListings
import com.g7.listing.findListings
import com.g7.listing.listingHistory
import com.g7.listing.searchListings
import com.g7.user.NewUser
import com.g7.user.createUsers
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ListingTest : DbTest() {
    private val seller = NewUser("seller@example.com", "Sara")

    @Suppress("LongParameterList")
    private fun listing(
        title: String,
        category: String = "vehicles.bikes",
        price: String? = "1500.00",
        lon: Double = 10.40,
        lat: Double = 55.39,
        description: String = "",
        kind: ListingKind = ListingKind.sale,
    ) = NewListing(
        sellerId = seller.id,
        title = title,
        category = category,
        price = price?.toBigDecimal(),
        lon = lon,
        lat = lat,
        city = "Odense",
        description = description,
        kind = kind,
    )

    private val bike = listing("Red Bianchi bicycle", description = "Road bike")
    private val car =
        listing(
            "Volvo V70 estate",
            "vehicles",
            "50000.00",
            12.57,
            55.68,
            "Family car",
        )
    private val lamp =
        listing(
            "Brass desk lamp",
            "home",
            "200.00",
            10.392,
            55.401,
            "Works fine",
        )
    private val gnome =
        listing(
            "Free garden gnome",
            "home",
            null,
            10.43,
            55.38,
            kind = ListingKind.free,
        )
    private val market = listOf(bike, car, lamp, gnome)

    private val origin = SearchFilter(lon = 10.39, lat = 55.40)

    @BeforeTest
    fun seedSeller() = db.createUsers(listOf(seller))

    private fun find(vararg listings: NewListing) =
        db.findListings(listings.map { it.id }).associateBy { it.id }

    private fun search(filter: SearchFilter) =
        db.searchListings(filter).map { it.id }

    @Suppress("LongParameterList")
    private fun edit(
        listing: NewListing,
        version: Long = 1,
        reason: String = "edit",
        sellerId: UUID = seller.id,
        title: String? = null,
        price: String? = null,
        status: ListingStatus? = null,
    ) = ListingEdit(
        listingId = listing.id,
        sellerId = sellerId,
        expectedVersion = version,
        reason = reason,
        title = title,
        price = price?.toBigDecimal(),
        status = status,
    )

    @Test
    fun `create then find round trips`() {
        val full =
            bike.copy(
                tags = listOf("vintage", "steel"),
                condition = ListingCondition.good,
                negotiable = true,
                delivery =
                    listOf(
                        DeliveryOption.pickup,
                        DeliveryOption.shipping,
                    ),
            )
        db.createListings(listOf(full, gnome))

        val found = find(full, gnome)

        val l = found.getValue(full.id)
        assertEquals(seller.id, l.sellerId)
        assertEquals("Sara", l.sellerName)
        assertEquals(1L, l.version)
        assertEquals("vehicles.bikes", l.category)
        assertEquals(listOf("vintage", "steel"), l.tags)
        assertEquals(ListingKind.sale, l.kind)
        assertEquals(ListingStatus.active, l.status)
        assertEquals(ListingCondition.good, l.condition)
        assertEquals(0, BigDecimal("1500").compareTo(l.price))
        assertEquals("dkk", l.currency)
        assertEquals(true, l.negotiable)
        assertEquals(listOf("pickup", "shipping"), l.delivery)
        assertEquals(55.39, assertNotNull(l.lat), 1e-9)
        assertNull(found.getValue(gnome.id).price)
        assertNull(found.getValue(gnome.id).currency)
    }

    @Test
    fun `creating many listings is a single batch`() {
        val many = (1..20).map { listing("Bicycle number $it") }

        assertEquals(1, executions { createListings(many) })

        assertEquals(20, db.findListings(many.map { it.id }).size)
    }

    @Test
    fun `editing many listings is a single statement`() {
        val many = (1..20).map { listing("Bicycle number $it") }
        db.createListings(many)
        val edits = many.map { edit(it, title = "Renamed ${it.title}") }

        assertEquals(1, executions { editListings(edits) })

        val found = find(*many.toTypedArray()).values
        assertEquals(20, found.count { it.version == 2L })
    }

    @Test
    fun `one failing row rolls back the whole batch`() {
        val broken = bike.copy(id = UUID.randomUUID(), currency = "dkk1")

        assertFailsWith<DataAccessException> {
            db.createListings(listOf(car, broken))
        }

        assertEquals(emptyMap(), find(car, broken))
    }

    @Test
    fun `search narrows by text`() {
        db.createListings(market)

        assertEquals(listOf(bike.id), search(origin.copy(text = "bicycle")))
        assertEquals(listOf(car.id), search(origin.copy(text = "family")))
        assertEquals(emptyList(), search(origin.copy(text = "boat")))
    }

    @Test
    fun `search narrows by category subtree and kind`() {
        db.createListings(market)

        val vehicles = search(origin.copy(category = "vehicles"))
        assertEquals(setOf(bike.id, car.id), vehicles.toSet())
        val bikes = search(origin.copy(category = "vehicles.bikes"))
        assertEquals(listOf(bike.id), bikes)
        val free = search(origin.copy(kind = ListingKind.free))
        assertEquals(listOf(gnome.id), free)
    }

    @Test
    fun `search narrows by price`() {
        db.createListings(market)

        val mid =
            origin.copy(
                minPrice = BigDecimal(1000),
                maxPrice = BigDecimal(2000),
            )
        assertEquals(listOf(bike.id), search(mid))
        val cheap = search(origin.copy(maxPrice = BigDecimal(500)))
        assertEquals(listOf(lamp.id), cheap)
    }

    @Test
    fun `search orders nearest first, pages and limits by radius`() {
        db.createListings(market)

        val all = listOf(lamp.id, bike.id, gnome.id, car.id)
        assertEquals(all, search(origin))
        assertEquals(all.take(2), search(origin.copy(limit = 2)))
        assertEquals(all.drop(2), search(origin.copy(limit = 2, offset = 2)))
        assertEquals(all.take(2), search(origin.copy(radiusM = 2_000)))
        assertEquals(emptyList(), search(origin.copy(radiusM = 50)))
    }

    @Test
    fun `edits change only what they name`() {
        db.createListings(market)

        db.editListings(
            listOf(
                edit(bike, reason = "reprice", price = "1200.00"),
                ListingEdit(
                    listingId = lamp.id,
                    sellerId = seller.id,
                    expectedVersion = 1,
                    reason = "rework",
                    title = "Brass lamp deluxe",
                    description = "New shade",
                    quantity = 2,
                    negotiable = true,
                ),
            ),
        )

        val found = find(bike, lamp)
        val b = found.getValue(bike.id)
        assertEquals(2L, b.version)
        assertEquals(0, BigDecimal("1200").compareTo(b.price))
        assertEquals("dkk", b.currency)
        assertEquals("Red Bianchi bicycle", b.title)
        val l = found.getValue(lamp.id)
        assertEquals("Brass lamp deluxe", l.title)
        assertEquals(2, l.quantity)
        assertEquals(true, l.negotiable)
        assertEquals(listOf(lamp.id), search(origin.copy(text = "shade")))
        assertEquals(emptyList(), search(origin.copy(text = "works")))
    }

    @Test
    fun `reserving hides from search and removing hides from find`() {
        db.createListings(market)

        db.editListings(
            listOf(
                edit(bike, status = ListingStatus.reserved),
                edit(lamp, status = ListingStatus.removed),
            ),
        )

        assertEquals(listOf(gnome.id, car.id), search(origin))
        val found = find(bike, lamp)
        assertEquals(setOf(bike.id), found.keys)
        assertEquals(ListingStatus.reserved, found.getValue(bike.id).status)
    }

    @Test
    fun `stale or foreign edits roll back their whole batch`() {
        db.createListings(market)
        val fine = edit(car, title = "Volvo changed")
        val stale = edit(bike, version = 7)
        val foreign = edit(bike, sellerId = UUID.randomUUID())

        listOf(stale, foreign).forEach { bad ->
            assertFailsWith<StaleVersionException> {
                db.editListings(listOf(fine, bad))
            }
        }

        assertEquals("Volvo V70 estate", find(car).getValue(car.id).title)
    }

    @Test
    fun `history lists every version newest first`() {
        db.createListings(listOf(bike))
        db.editListings(listOf(edit(bike, 1, "reprice", price = "1200.00")))
        db.editListings(
            listOf(edit(bike, 2, "hold", status = ListingStatus.reserved)),
        )
        db.editListings(
            listOf(edit(bike, 3, "handover", status = ListingStatus.sold)),
        )

        val history = db.listingHistory(bike.id)

        assertEquals(listOf(4L, 3L, 2L, 1L), history.map { it.version })
        assertEquals(
            listOf("handover", "hold", "reprice", null),
            history.map { it.reason },
        )
        assertEquals(
            listOf(
                ListingStatus.sold,
                ListingStatus.reserved,
                ListingStatus.active,
                ListingStatus.active,
            ),
            history.map { it.status },
        )
        assertEquals(0, BigDecimal("1500").compareTo(history.last().price))
        assertEquals(4L, find(bike).getValue(bike.id).version)
    }
}
