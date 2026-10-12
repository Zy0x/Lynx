package com.noir.lynx.designsystem.controls

import org.junit.Assert.*
import org.junit.Test

class SliderGestureSessionTest {
    private fun session(steps: Int = 0, rtl: Boolean = false) =
        SliderGestureSession(50f, 0f, 100f, steps, 200f, 10f, rtl)

    @Test fun tapAndSubSlopMovementNeverCommit() {
        val gesture = session()
        assertNull(gesture.releasedValue())
        assertFalse(gesture.move(9f, 2f))
        assertNull(gesture.releasedValue())
    }

    @Test fun verticalAndDiagonalRemainRejectedEvenIfTheyTurnHorizontal() {
        for ((dx, dy) in listOf(2f to 40f, 20f to 20f, 18f to 13f)) {
            val gesture = session()
            assertFalse(gesture.move(dx, dy))
            assertFalse(gesture.move(100f, 0f))
            assertNull(gesture.releasedValue())
        }
    }

    @Test fun horizontalPreviewDoesNotJumpToTouchLocationAndCanReverse() {
        val gesture = session()
        assertTrue(gesture.move(50f, 2f))
        assertEquals(70f, gesture.preview, 0.001f)
        assertTrue(gesture.move(-50f, 2f))
        assertEquals(30f, gesture.releasedValue()!!, 0.001f)
    }

    @Test fun cancelDiscardsHorizontalPreview() {
        val gesture = session()
        gesture.move(100f, 0f)
        gesture.cancel()
        assertNull(gesture.releasedValue())
        assertEquals(50f, gesture.preview, 0f)
    }

    @Test fun steppedValuesAndBoundsAreRespected() {
        val gesture = session(steps = 9)
        gesture.move(43f, 0f)
        assertEquals(70f, gesture.preview, 0f)
        gesture.move(500f, 0f)
        assertEquals(100f, gesture.preview, 0f)
        gesture.move(-500f, 0f)
        assertEquals(0f, gesture.preview, 0f)
    }

    @Test fun rtlReversesHorizontalMapping() {
        val gesture = session(rtl = true)
        gesture.move(50f, 0f)
        assertEquals(30f, gesture.releasedValue()!!, 0.001f)
    }

    @Test fun unchangedReleaseDoesNotCommit() {
        val gesture = session()
        gesture.move(100f, 0f)
        gesture.move(0f, 0f)
        assertNull(gesture.releasedValue())
    }
}
