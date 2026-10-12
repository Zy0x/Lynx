package com.noir.lynx.designsystem.controls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises actual Compose input routing with fake callbacks, never root/hardware writes. */
class SafeSliderTest {
    @get:Rule val rule = createComposeRule()
    private val commits = mutableListOf<Float>()
    private var finishes = 0
    private lateinit var scrollState: ScrollState
    private var value by mutableFloatStateOf(50f)
    private var visible by mutableStateOf(true)

    private fun host(enabled: Boolean = true, steps: Int = 0) {
        rule.setContent {
            val scroll = rememberScrollState()
            SideEffect { scrollState = scroll }
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Spacer(Modifier.height(160.dp))
                if (visible) SafeSlider(value, { value = it; commits.add(it) },
                    modifier = Modifier.testTag("slider"), enabled = enabled,
                    valueRange = 0f..100f, steps = steps,
                    onValueChangeFinished = { finishes++ })
                Spacer(Modifier.height(2400.dp))
            }
        }
    }

    @Test fun tapTrackDoesNotChangeValue() {
        host()
        rule.onNodeWithTag("slider").performTouchInput { click(Offset(width * 0.8f, centerY)) }
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(0, finishes) }
    }

    @Test fun verticalDragScrollsWithoutCommit() {
        host()
        rule.onNodeWithTag("slider").performTouchInput {
            swipe(center, center + Offset(0f, -120f), 300)
        }
        rule.runOnIdle { assertTrue("Vertical scroll committed", commits.isEmpty()); assertTrue("Parent did not scroll", scrollState.value > 0) }
    }

    @Test fun diagonalDragScrollsWithoutCommit() {
        host()
        rule.onNodeWithTag("slider").performTouchInput {
            swipe(center, center + Offset(60f, -120f), 300)
        }
        rule.runOnIdle { assertTrue("Diagonal scroll committed", commits.isEmpty()); assertTrue("Parent did not scroll", scrollState.value > 0) }
    }

    @Test fun horizontalDragCommitsOnceAtReleaseOnly() {
        host()
        val slider = rule.onNodeWithTag("slider")
        slider.performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(50f, value, 0f) }
        slider.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, commits.size); assertEquals(1, finishes); assertTrue(value > 50f) }
    }

    @Test fun horizontalReverseCommitsFinalDirection() {
        host()
        rule.onNodeWithTag("slider").performTouchInput {
            down(center); moveBy(Offset(120f, 0f)); moveBy(Offset(-240f, 0f)); up()
        }
        rule.runOnIdle { assertEquals(1, commits.size); assertTrue(value < 50f) }
    }

    @Test fun cancelDiscardsPreview() {
        host()
        rule.onNodeWithTag("slider").performTouchInput {
            down(center); moveBy(Offset(120f, 0f)); cancel()
        }
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(0, finishes) }
    }

    @Test fun secondPointerCancelsRatherThanChangingOwner() {
        host()
        rule.onNodeWithTag("slider").performTouchInput {
            down(0, center); moveBy(Offset(120f, 0f))
            down(1, center + Offset(10f, 0f)); up(1); up(0)
        }
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(0, finishes) }
    }

    @Test fun leavingPageDiscardsPreview() {
        host()
        rule.onNodeWithTag("slider").performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        rule.runOnIdle { visible = false }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(0, finishes) }
    }

    @Test fun pollingDoesNotMovePreviewOrRestartGesture() {
        host()
        val slider = rule.onNodeWithTag("slider")
        slider.performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        val preview = slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        rule.runOnIdle { value = 10f }
        rule.waitForIdle()
        assertEquals(preview, slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        slider.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, commits.size); assertEquals(preview, value, 0f) }
    }

    @Test fun accessibilitySetProgressCommitsSteppedValue() {
        host(steps = 9)
        rule.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.SetProgress) { it(73f) }
        rule.runOnIdle { assertEquals(70f, value, 0f); assertEquals(1, finishes) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun keyboardAdjustmentCommitsOnce() {
        host(steps = 9)
        rule.onNodeWithTag("slider").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        rule.onNodeWithTag("slider").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        rule.runOnIdle { assertEquals(60f, value, 0f); assertEquals(1, finishes) }
    }

    @Test fun disabledSliderCannotCommit() {
        host(enabled = false)
        rule.onNodeWithTag("slider").assertIsNotEnabled().performTouchInput {
            swipe(center, center + Offset(120f, 0f), 300)
        }
        rule.runOnIdle { assertTrue(commits.isEmpty()) }
    }

    @Test fun cancelledDragReturnsToNewestPolledValue() {
        host()
        val slider = rule.onNodeWithTag("slider")
        slider.performTouchInput { down(center); moveBy(Offset(120f, 0f)) }
        rule.runOnIdle { value = 20f }
        slider.performTouchInput { cancel() }
        rule.runOnIdle { assertTrue(commits.isEmpty()); assertEquals(20f, value, 0f) }
        slider.assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(20f, 0f..100f))
    }

    @Test fun labeledAdapterSuppliesFinalValueBeforeFinishedCallback() {
        var applied: Float? = null
        rule.setContent {
            LynxSlider(label = "Test", value = value, onValueChange = { value = it },
                onValueChangeFinished = { applied = value }, valueRange = 0f..100f,
                displayValue = value.toString(), modifier = Modifier.testTag("label"))
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput {
            down(center); moveBy(Offset(120f, 0f))
        }
        rule.runOnIdle { assertNull(applied); assertEquals(50f, value, 0f) }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performTouchInput { up() }
        rule.runOnIdle { assertNotNull(applied); assertEquals(value, applied!!, 0f); assertTrue(value > 50f) }
    }
}
