package com.g7.user

import com.g7.database.jooq.Tables.CURRENT_USERS
import com.g7.database.jooq.Tables.USERS
import com.g7.database.jooq.Tables.USER_VERSIONS
import com.g7.db.arg
import com.g7.db.batchRows
import com.g7.db.column
import com.g7.db.fetchRows
import com.g7.db.read
import org.jooq.DSLContext
import org.jooq.Record
import java.util.UUID

data class NewUser(
    val email: String,
    val displayName: String,
    val phone: String? = null,
    val locale: String = "da-dk",
    val lon: Double? = null,
    val lat: Double? = null,
    val id: UUID = UUID.randomUUID(),
)

data class ProfileEdit(
    val userId: UUID,
    val expectedVersion: Long,
    val displayName: String? = null,
    val phone: String? = null,
    val bio: String? = null,
)

data class UserProfile(
    val id: UUID,
    val version: Long,
    val email: String,
    val displayName: String,
    val phone: String?,
    val bio: String?,
    val avatarUrl: String?,
    val locale: String,
    val lon: Double?,
    val lat: Double?,
)

fun DSLContext.createUsers(users: List<NewUser>) =
    batchRows(INSERT, users.map { it.toRow() })

fun DSLContext.editProfiles(edits: List<ProfileEdit>) =
    batchRows(EDIT, edits.map { it.toRow() })

fun DSLContext.findUsers(ids: List<UUID>): List<UserProfile> =
    fetchRows(SELECT, "ids" to ids.toTypedArray())
        .map { it.toUserProfile() }

private const val INSERT = """
with u as (
    insert into users (id, current_version, current_email)
    values (:id, 1, :email)
)
insert into user_versions (
    user_id,
    version,
    email,
    display_name,
    phone,
    locale,
    home_location
)
values (
    :id,
    1,
    :email,
    :display_name,
    :phone,
    :locale,
    cast(st_setsrid(
        st_makepoint(cast(:lon as float8),
        cast(:lat as float8)), 4326
    ) as geography)
)
"""

private const val EDIT = """
with cur as (
    select v.*
    from user_versions v
    join
        users u on u.id = v.user_id
        and u.current_version = v.version
    where
        v.user_id = :id
        and v.version = :version
    for update of u
), ins as (
    insert into user_versions (
        user_id,
        version,
        prev_version,
        created_by,
        email,
        display_name,
        phone,
        bio,
        avatar_url,
        locale,
        home_location,
        is_deleted
    )
    select
        user_id,
        version + 1,
        version,
        user_id,
        email,
        coalesce(:display_name, display_name),
        coalesce(:phone, phone),
        coalesce(:bio, bio),
        avatar_url,
        locale,
        home_location,
        is_deleted
    from cur
    returning user_id, version, email, is_deleted
)
update users u
set current_version = ins.version,
    current_email = ins.email,
    current_is_deleted = ins.is_deleted
from ins
where u.id = ins.user_id
"""

private const val SELECT = """
select
    user_id,
    version,
    cast(email as text) as email,
    display_name,
    phone,
    bio,
    avatar_url,
    locale,
    st_x(cast(home_location as geometry)) as lon,
    st_y(cast(home_location as geometry)) as lat
from current_users
where user_id = any(:ids)
"""

private val EMAIL = column("email", String::class.java)
private val LON = column("lon", Double::class.javaObjectType)
private val LAT = column("lat", Double::class.javaObjectType)

private fun NewUser.toRow() =
    mapOf(
        USERS.ID.arg(id),
        USER_VERSIONS.EMAIL.arg(email),
        USER_VERSIONS.DISPLAY_NAME.arg(displayName),
        USER_VERSIONS.PHONE.arg(phone),
        USER_VERSIONS.LOCALE.arg(locale),
        "lon" to lon,
        "lat" to lat,
    )

private fun ProfileEdit.toRow() =
    mapOf(
        USERS.ID.arg(userId),
        USER_VERSIONS.VERSION.arg(expectedVersion),
        USER_VERSIONS.DISPLAY_NAME.arg(displayName),
        USER_VERSIONS.PHONE.arg(phone),
        USER_VERSIONS.BIO.arg(bio),
    )

private fun Record.toUserProfile() =
    UserProfile(
        id = read(CURRENT_USERS.USER_ID),
        version = read(CURRENT_USERS.VERSION),
        email = read(EMAIL),
        displayName = read(CURRENT_USERS.DISPLAY_NAME),
        phone = read(CURRENT_USERS.PHONE),
        bio = read(CURRENT_USERS.BIO),
        avatarUrl = read(CURRENT_USERS.AVATAR_URL),
        locale = read(CURRENT_USERS.LOCALE),
        lon = read(LON),
        lat = read(LAT),
    )
