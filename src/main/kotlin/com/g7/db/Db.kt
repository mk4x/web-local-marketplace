package com.g7.db

import com.fasterxml.jackson.databind.json.JsonMapper
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

private val mapper = JsonMapper()

fun <T> Field<T>.arg(value: T?): Pair<String, Any?> =
    name to (if (value is EnumType) value.literal else value)

fun <T> column(
    name: String,
    type: Class<T>,
): Field<T> = DSL.field(DSL.name(name), type)

fun <T> Record.read(field: Field<T>): T = get(field.name, field.type)

fun DSLContext.fetchRows(
    sql: String,
    args: Row,
): Result<Record> = fetch(sql, mapper.writeValueAsString(args))

fun DSLContext.writeRows(
    sql: String,
    rows: List<Row>,
) {
    if (rows.isEmpty()) return
    transaction { tx ->
        val written = tx.dsl().execute(sql, mapper.writeValueAsString(rows))
        val missed = rows.size - written
        if (missed != 0) {
            throw StaleVersionException("No row matched for $missed entries")
        }
    }
}
