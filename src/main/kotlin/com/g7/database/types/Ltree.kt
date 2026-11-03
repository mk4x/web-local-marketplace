package com.g7.database.types

@JvmInline
value class Ltree(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Ltree path cannot be blank" }
    }

    override fun toString(): String = value
}
