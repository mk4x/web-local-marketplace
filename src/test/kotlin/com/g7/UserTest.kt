package com.g7

import com.g7.db.StaleVersionException
import com.g7.user.NewUser
import com.g7.user.ProfileEdit
import com.g7.user.createUsers
import com.g7.user.editProfiles
import com.g7.user.findUsers
import org.jooq.exception.DataAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserTest : DbTest() {
    private val anna =
        NewUser(
            email = "Anna@Example.com",
            displayName = "Anna",
            phone = "+4512345678",
            lon = 10.39,
            lat = 55.40,
        )
    private val bo = NewUser(email = "bo@example.com", displayName = "Bo")

    private fun find(vararg users: NewUser) =
        db.findUsers(users.map { it.id }).associateBy { it.id }

    @Test
    fun `create then find round trips`() {
        db.createUsers(listOf(anna, bo))

        val found = find(anna, bo)

        val a = found.getValue(anna.id)
        assertEquals("Anna@Example.com", a.email)
        assertEquals("+4512345678", a.phone)
        assertEquals(1L, a.version)
        assertEquals(55.40, assertNotNull(a.lat), 1e-9)
        assertEquals(10.39, assertNotNull(a.lon), 1e-9)
        val b = found.getValue(bo.id)
        assertEquals("da-dk", b.locale)
        assertNull(b.lat)
    }

    @Test
    fun `creating many users is a single batch`() {
        val users = (1..25).map { NewUser("u$it@example.com", "User $it") }

        assertEquals(1, executions { createUsers(users) })
        assertEquals(25, find(*users.toTypedArray()).size)
    }

    @Test
    fun `values are bound, never interpolated`() {
        val hostile = NewUser("e@example.com", "Robert'); drop table users;--")

        db.createUsers(listOf(hostile))

        val stored = find(hostile).getValue(hostile.id)
        assertEquals(hostile.displayName, stored.displayName)
    }

    @Test
    fun `one failing row rolls back the whole batch`() {
        db.createUsers(listOf(anna))
        val sameEmailOtherCase = NewUser("ANNA@example.com", "Impostor")

        assertFailsWith<DataAccessException> {
            db.createUsers(listOf(bo, sameEmailOtherCase))
        }

        assertEquals(setOf(anna.id), find(anna, bo, sameEmailOtherCase).keys)
    }

    @Test
    fun `edits add a version and keep unspecified fields`() {
        db.createUsers(listOf(anna, bo))

        db.editProfiles(
            listOf(
                ProfileEdit(anna.id, 1, displayName = "Anna B", bio = "Fan"),
                ProfileEdit(bo.id, 1, phone = "+4587654321"),
            ),
        )

        val found = find(anna, bo)
        val a = found.getValue(anna.id)
        assertEquals(2L, a.version)
        assertEquals("Anna B", a.displayName)
        assertEquals("Fan", a.bio)
        assertEquals("+4512345678", a.phone)
        assertEquals(55.40, assertNotNull(a.lat), 1e-9)
        val b = found.getValue(bo.id)
        assertEquals("+4587654321", b.phone)
        assertEquals("Bo", b.displayName)
    }

    @Test
    fun `a stale edit rolls back the other edits in its batch`() {
        db.createUsers(listOf(anna, bo))
        db.editProfiles(listOf(ProfileEdit(bo.id, 1, bio = "first")))

        assertFailsWith<StaleVersionException> {
            db.editProfiles(
                listOf(
                    ProfileEdit(anna.id, 1, bio = "fresh"),
                    ProfileEdit(bo.id, 1, bio = "stale"),
                ),
            )
        }

        val found = find(anna, bo)
        assertNull(found.getValue(anna.id).bio)
        assertEquals("first", found.getValue(bo.id).bio)
    }
}
