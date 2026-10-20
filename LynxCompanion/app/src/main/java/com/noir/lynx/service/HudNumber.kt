package com.noir.lynx.service

import java.util.Locale

internal fun hudNumber(value: Int): String = if (value == Int.MIN_VALUE) "—" else value.toString()
internal fun hudNumber(value: Float): String = if (value.isFinite()) String.format(Locale.getDefault(), "%.1f", value) else "—"
