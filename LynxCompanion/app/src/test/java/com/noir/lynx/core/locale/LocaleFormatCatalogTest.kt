package com.noir.lynx.core.locale

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the same Java formatter used by Android Resources.getString(id, args). */
class LocaleFormatCatalogTest {
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
                        String.format(Locale.US, template, *args)
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
