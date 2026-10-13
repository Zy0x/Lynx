package com.noir.lynx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MtkGedFrequencyParserTest {
    @Test
    fun parsesGedOppIndexAndFrequencyInKhz() {
        assertEquals(300, MtkGedFrequencyParser.parseCurrentFrequencyMhz("48 300000\n"))
    }

    @Test
    fun parsesSingleFrequencyInKhz() {
        assertEquals(300, MtkGedFrequencyParser.parseCurrentFrequencyMhz("300000"))
    }

    @Test
    fun parsesFrequencyInHz() {
        assertEquals(300, MtkGedFrequencyParser.parseCurrentFrequencyMhz("300000000"))
    }

    @Test
    fun rejectsEmptyOrNonNumericInput() {
        assertNull(MtkGedFrequencyParser.parseCurrentFrequencyMhz(""))
        assertNull(MtkGedFrequencyParser.parseCurrentFrequencyMhz("48 unknown"))
    }
}
