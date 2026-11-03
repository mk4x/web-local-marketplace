package com.g7.database.types

data class TstzMultiRange(
    val ranges: List<TstzRange>,
) {
    init {
        require(
            ranges.none { it.empty },
        ) { "Multiranges cannot contain empty ranges" }
    }
}
