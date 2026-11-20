package com.g7.category

import com.g7.database.jooq.Tables.CATEGORIES
import com.g7.db.column
import com.g7.db.read
import org.jooq.DSLContext
import org.jooq.Record

data class Category(
    val path: String,
    val label: String,
)

fun DSLContext.categories(): List<Category> =
    fetch(SELECT).map { it.toCategory() }

private const val SELECT = """
select
    cast(path as text) as path,
    label
from categories
order by path
"""

private val PATH = column("path", String::class.java)

private fun Record.toCategory() =
    Category(
        path = read(PATH),
        label = read(CATEGORIES.LABEL),
    )
