package com.noir.lynx.service

import com.noir.lynx.data.LynxRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LynxAppAutomationBaselineTest {

    private var prevServiceBaseline: String = "balance"
    private var prevRepoBaseline: String? = null

    @Before
    fun setUp() {
        prevServiceBaseline = LynxAppAutomationService.cachedBaseline
        prevRepoBaseline = LynxRepository.cachedBaselineProfile
        LynxAppAutomationService.cachedBaseline = "balance"
        LynxRepository.cachedBaselineProfile = "balance"
    }

    @After
    fun tearDown() {
        LynxAppAutomationService.cachedBaseline = prevServiceBaseline
        LynxRepository.cachedBaselineProfile = prevRepoBaseline
    }

    @Test
    fun transientOrBroadcastProfileChangeDoesNotOverwriteCachedBaseline() {
        LynxAppAutomationService.notifyProfileChanged("performance", isManual = false)

        assertEquals("balance", LynxAppAutomationService.cachedBaseline)
        assertEquals("balance", LynxRepository.cachedBaselineProfile)
    }

    @Test
    fun manualProfileChangeUpdatesCachedBaselineExcludingDormant() {
        LynxAppAutomationService.notifyProfileChanged("extreme", isManual = true)

        assertEquals("extreme", LynxAppAutomationService.cachedBaseline)
        assertEquals("extreme", LynxRepository.cachedBaselineProfile)

        LynxAppAutomationService.notifyProfileChanged("dormant", isManual = true)
        assertEquals("extreme", LynxAppAutomationService.cachedBaseline)
        assertEquals("extreme", LynxRepository.cachedBaselineProfile)
    }
}
