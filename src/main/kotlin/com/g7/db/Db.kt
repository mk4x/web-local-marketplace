package com.g7.db

import org.jooq.DSLContext
import org.jooq.EnumType
import org.jooq.Field
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

typealias Row = Map<String, Any?>

class StaleVersionException(
    message: String,
) : RuntimeException(message)

private val placeholder = Regex("(?<!:):([a-z_]+)")

private fun String.positional() = placeholder.replace(this, "?")

private fun String.bind(row: Row): Array<Any?> =
    placeholder
        .findAll(this)
        .map { row.getValue(it.groupValues[1]) }
        .toList()
        .toTypedArray()

fun <T> Field<T>.arg(value: T?): Pair<String, Any?> =
    name to (if (value is EnumType) value.literal else value)

fun <T> column(
    name: String,
    type: Class<T>,
): Field<T> = DSL.field(DSL.name(name), type)

fun <T> Record.read(field: Field<T>): T = get(field.name, field.type)

@Suppress("SpreadOperator")
fun DSLContext.fetchRows(
    sql: String,
    vararg args: Pair<String, Any?>,
): Result<Record> = fetch(sql.positional(), *sql.bind(args.toMap()))

@Suppress("SpreadOperator")
fun DSLContext.batchRows(
    sql: String,
    rows: List<Row>,
) {
    if (rows.isEmpty()) return
    transaction { tx ->
        val batch = tx.dsl().batch(sql.positional())
        rows.forEach { batch.bind(*sql.bind(it)) }
        val unmatched = batch.execute().withIndex().filter { it.value == 0 }
        if (unmatched.isNotEmpty()) {
            val indices = unmatched.map { it.index }
            throw StaleVersionException("No row matched for entries $indices")
        }
    }
}
