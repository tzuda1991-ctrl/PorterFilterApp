package com.zuauto.porter.vip

import java.math.BigDecimal

internal object OrderFare {
    private val moneyLabel = Regex(
        """(?:₹|INR|Rs\.?)\s*(\d+(?:,\d{2,3})*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    internal fun fromLabel(text: String): BigDecimal? =
        moneyLabel.matchEntire(text.trim())?.groupValues?.get(1)
            ?.replace(",", "")?.toBigDecimalOrNull()?.stripTrailingZeros()
            ?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal(Int.MAX_VALUE) }


    fun distinctFare(labels: Iterable<String>): BigDecimal? =
        labels.mapNotNull(::fromLabel).distinct().singleOrNull()
}
