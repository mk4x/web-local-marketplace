package com.g7.database.types

import java.time.Instant

data class TstzRange(
    val lower: Instant?,
    val upper: Instant?,
    val lowerInclusive: Boolean = true,
    val upperInclusive: Boolean = false,
    val empty: Boolean = false,
) {
    init {
        require(!empty || (lower == null && upper == null)) {
            "An empty range cannot have bounds"
        }
        require(lower == null || upper == null || lower <= upper) {
            "Lower bound cannot be after upper bound"
        }
    }
}
