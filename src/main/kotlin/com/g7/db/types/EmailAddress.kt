package com.g7.db.types

@JvmInline
value class EmailAddress(
    val address: String,
) {
    init {
        require(address.isNotEmpty()) {
            "Email address shouldn't be empty"
        }
        require(address.contains('@')) {
            "Email address should contain '@'"
        }
        require(address.contains('@')) {
            "Email address should contain '@'"
        }

        fun asString(): String = address
    }
}
