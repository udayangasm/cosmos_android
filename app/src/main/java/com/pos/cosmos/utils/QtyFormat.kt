package com.pos.cosmos.utils

import java.util.Locale

/**
 * Renders a quantity for display or for the API payload.
 *
 * Quantities are `Double` end-to-end (the backend's `OrderItems.qty` and
 * `freeIssueQty` are both `double`), so fractional lines like 0.5 kg are valid
 * data. Rendering them with `toInt()` or `"%.0f"` silently turns 0.5 into "0"
 * and 1.5 into "2" — use this instead.
 *
 * Whole numbers render without a decimal point ("2", not "2.0") so existing
 * receipts and lists look unchanged; fractions keep up to [maxDecimals] digits
 * with trailing zeros trimmed ("0.5", "0.25").
 */
fun formatQty(qty: Double, maxDecimals: Int = 3): String {
    if (qty.isNaN() || qty.isInfinite()) return "0"
    if (qty == Math.floor(qty) && !qty.isInfinite()) return qty.toLong().toString()
    return String.format(Locale.US, "%.${maxDecimals}f", qty)
        .trimEnd('0')
        .trimEnd('.')
}
