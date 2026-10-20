package com.noir.lynx.core.locale

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercise the same formatter used by AppLocale for HUD and other localized text. */
class LocaleFormatCatalogTest {
    @Test
    fun hudLabelsWithoutArgumentsPreserveLiteralPercentSignsInEveryLocale() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        for (folder in listOf("values", "values-id", "values-en")) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(root, "$folder/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            var foundLow = false
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes.getNamedItem("name").nodeValue
                if (!name.startsWith("hud_")) continue
                val value = node.textContent
                assertEquals("$folder: $name", value, formatLocalizedString(value, Locale.US))
                if (name == "hud_metric_low") {
                    assertEquals("1% low", formatLocalizedString(value, Locale.US))
                    foundLow = true
                }
            }
            assertTrue("$folder must exercise the crashing HUD label", foundLow)
        }
    }

    @Test
    fun parameterizedStringsKeepResourceLocaleAndEscapedPercent() {
        assertEquals("Load 42%", formatLocalizedString("Load %1\$d%%", Locale.US, 42))
        assertEquals("16,7 ms", formatLocalizedString("%.1f ms", Locale.GERMANY, 16.7))
    }

    @Test
    fun everyParameterizedCatalogEntryCanBeFormatted() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val placeholder = Regex("%(\\d+)\\$([sd])")
        var count = 0
        for (folder in listOf("values", "values-id", "values-en")) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, "$folder/strings.xml"))
            for (tag in listOf("string", "item")) {
                val nodes = document.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index)
                    val template = node.textContent
                    val matches = placeholder.findAll(template).toList()
                    if (matches.isEmpty()) continue
                    val args = arrayOfNulls<Any>(matches.maxOf { it.groupValues[1].toInt() })
                    for (match in matches) args[match.groupValues[1].toInt()-1] =
                        if (match.groupValues[2] == "d") 42 else "sample"
                    try {
                        formatLocalizedString(template, Locale.US, *args)
                    } catch (error: java.util.IllegalFormatException) {
                        throw AssertionError("$folder: ${node.attributes.getNamedItem("name")?.nodeValue ?: tag}: $template", error)
                    }
                    count++
                }
            }
        }
        assertTrue("No formatted resources were exercised", count > 100)
    }
}
