package com.noir.lynx.receiver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LynxBootReceiverPolicyTest {

    @Test
    fun skipsDuplicateApplyWhenDaemonAlreadyAppliedMatchingBaseline() {
        assertFalse(LynxBootReceiver.shouldApplyBootProfile("1794938000|balance", true, "balance"))
        assertFalse(LynxBootReceiver.shouldApplyBootProfile("1794938000|balance", true, "balanced"))
        assertFalse(LynxBootReceiver.shouldApplyBootProfile("1794938000|balance", true, "auto"))
        assertFalse(LynxBootReceiver.shouldApplyBootProfile("1794938000|performance", true, "performance"))
    }

    @Test
    fun appliesProfileWhenDaemonDeadOrMarkerMissingOrMismatched() {
        assertTrue(LynxBootReceiver.shouldApplyBootProfile("1794938000|balance", false, "balance"))
        assertTrue(LynxBootReceiver.shouldApplyBootProfile("", true, "balance"))
        assertTrue(LynxBootReceiver.shouldApplyBootProfile(null, true, "balance"))
        assertTrue(LynxBootReceiver.shouldApplyBootProfile("1794938000|balance", true, "extreme"))
    }
}
