package com.noir.lynx.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import androidx.annotation.StringRes

/** Stores the Companion-only language override and applies it to Activity resources. */
object AppLocale {
    private const val PREFS = "lynx_app_preferences"
    private const val KEY_OVERRIDE = "language_override"
    const val SYSTEM = "system"
    const val INDONESIAN = "id"
    const val ENGLISH = "en"

    fun selected(context: Context): String = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_OVERRIDE, SYSTEM)
        ?.takeIf { it == INDONESIAN || it == ENGLISH }
        ?: SYSTEM

    fun setSelected(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OVERRIDE, if (languageTag == INDONESIAN || languageTag == ENGLISH) languageTag else SYSTEM)
            .apply()
    }

    fun getString(context: Context, @StringRes resourceId: Int, vararg formatArgs: Any): String =
        wrap(context).getString(resourceId, *formatArgs)

    fun wrap(context: Context): Context {
        val languageTag = selected(context)
        if (languageTag == SYSTEM) {
            val systemLocale = context.resources.configuration.locales[0]
                ?: LocaleList.getDefault().get(0)
                ?: Locale("id")
            Locale.setDefault(systemLocale)
            return context
        }

        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
