package com.noir.lynx.core.locale

import java.util.Locale

/** Literal labels (including percent signs) must bypass the resource formatter. */
internal fun formatLocalizedString(value: String, locale: Locale, vararg args: Any?): String =
    if (args.isEmpty()) value else String.format(locale, value, *args)
