package com.noir.lynx.designsystem.controls

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/**
 * LynxTabPager — High-precision swipeable tab container for subscreen navigation.
 *
 * Architecture & Features:
 * 1. Synchronized Indicator: Real-time synchronization between active tab index and pager state.
 * 2. Gesture Priority: SafeSlider pointer consumption prevents horizontal pager swipe during active slider dragging.
 * 3. Haptic Feedback: Micro-haptic tick upon settling on a target page.
 * 4. Preserved Scroll State: Each page retains independent vertical scroll offsets without jump/reset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LynxTabPager(
    pageCount: Int,
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabRow: @Composable ((selectedPage: Int, onSelectPage: (Int) -> Unit) -> Unit)? = null,
    content: @Composable (page: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val safeCount = pageCount.coerceAtLeast(1)
    val initialIndex = selectedPage.coerceIn(0, safeCount - 1)

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { safeCount }
    )

    // Synchronize external tab clicks to animated page scrolling
    LaunchedEffect(selectedPage) {
        if (selectedPage != pagerState.currentPage && selectedPage in 0 until safeCount) {
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(
                    page = selectedPage,
                    animationSpec = tween(durationMillis = 240)
                )
            }
        }
    }

    // Synchronize settled page from gesture swipe back to parent with haptic confirmation
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled != selectedPage && settled in 0 until safeCount) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onPageSelected(settled)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        tabRow?.invoke(pagerState.currentPage) { targetPage ->
            if (targetPage != pagerState.currentPage && targetPage in 0 until safeCount) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(durationMillis = 240)
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            key = { page -> page },
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(1)
            )
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                content(page)
            }
        }
    }
}
