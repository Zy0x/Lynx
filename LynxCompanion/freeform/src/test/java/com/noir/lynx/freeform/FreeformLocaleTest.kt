package com.noir.lynx.freeform

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class FreeformLocaleTest {
    @Test fun extractedLibraryHasCompleteStandaloneCatalogsAndValidFormats() {
        val resources = File(System.getProperty("freeform.resources"))
        val catalogs = listOf("values", "values-id", "values-en").map { folder ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resources, "$folder/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            (0 until nodes.length).associate { index ->
                val item = nodes.item(index)
                item.attributes.getNamedItem("name").nodeValue to item.textContent
            }
        }
        assertEquals(catalogs[0].keys, catalogs[1].keys); assertEquals(catalogs[0].keys, catalogs[2].keys)
        for (catalog in catalogs) for ((_, value) in catalog) {
            val tokens = Regex("%(\\d+)\\${'$'}([sdf])").findAll(value).toList()
            val count = tokens.maxOfOrNull { it.groupValues[1].toInt() } ?: 0
            val args = Array<Any>(count) { "sample" }
            for (token in tokens) args[token.groupValues[1].toInt() - 1] = when (token.groupValues[2]) {
                "d" -> 1; "f" -> 1.0; else -> "sample"
            }
            if (count > 0) assertTrue(String.format(Locale.ROOT, value, *args).isNotBlank())
        }
    }
}
