package com.klin.read.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.klin.read.data.PageTurnMode
import com.klin.read.data.ReaderSettings
import com.klin.read.ui.design.Space

/**
 * Renders the chapter using the chosen page-turn mode.
 *
 * SCROLL uses the lazy paragraph list; SLIDE slices it into screens and moves
 * between them with a pager.
 *
 * Taps are handled by the host container rather than here.
 */
@Composable
fun PagedChapter(
    body: String,
    settings: ReaderSettings,
    chapterTitle: String,
    palette: ReadingPalette,
    startParagraph: Int,
    chapterIndex: Int,
    chapterCount: Int,
    onParagraphVisible: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (settings.pageTurn == PageTurnMode.SCROLL) {
        ChapterBody(
            body = body,
            settings = settings,
            chapterTitle = chapterTitle,
            palette = palette,
            startParagraph = startParagraph,
            hasNext = chapterIndex < chapterCount - 1,
            onParagraphVisible = onParagraphVisible,
            onReachedEnd = onNextChapter,
            modifier = modifier,
            scrollToParagraph = startParagraph.takeIf { it > 0 }
        )
    } else {
        // Keyed on the chapter so the pager is rebuilt when the chapter changes.
        //
        // Its state is remembered per composition slot, so switching chapters
        // reused the previous pager and kept its page index -- landing mid-chapter
        // in the new one. A key forces a fresh pager starting at page 0.
        androidx.compose.runtime.key(chapterIndex) {
            PagedReader(
                body = body,
                settings = settings,
                chapterTitle = chapterTitle,
                palette = palette,
                startParagraph = startParagraph,
                hasPrevious = chapterIndex > 0,
                hasNext = chapterIndex < chapterCount - 1,
                onParagraphVisible = onParagraphVisible,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                modifier = modifier
            )
        }
    }
}

/** Paragraphs per screen in the paged modes. */
private const val PARAGRAPHS_PER_PAGE = 16

@Composable
private fun PagedReader(
    body: String,
    settings: ReaderSettings,
    chapterTitle: String,
    palette: ReadingPalette,
    startParagraph: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onParagraphVisible: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paragraphs = remember(body) { splitParagraphs(body) }

    // chunked() on an empty list returns empty, which would leave the pager with
    // zero pages and index out of bounds. The sentinel keeps pageCount >= 1.
    val pages = remember(paragraphs) {
        paragraphs.chunked(PARAGRAPHS_PER_PAGE).ifEmpty { listOf(emptyList()) }
    }
    val pageCount = pages.size

    val initialPage = (startParagraph / PARAGRAPHS_PER_PAGE).coerceIn(0, pageCount - 1)

    // No boundary slots. An extra page at each end was meant to let a swipe carry
    // into the neighbouring chapter, but a fast fling passed through the slot and
    // kept going, so the new chapter opened several pages in.
    //
    // Instead the pager stays within the chapter, and a swipe that is already at
    // an edge changes chapter directly -- one gesture, one chapter, always at its
    // first page.
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                onParagraphVisible((page * PARAGRAPHS_PER_PAGE).coerceAtLeast(0))
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // Chapter advance at the edges.
                //
                // Two other approaches were tried and neither fired: a
                // `pointerInput` on the container (the pager consumes the drag
                // first) and a `nestedScroll` connection (the pager clamps at the
                // edge instead of producing overscroll).
                //
                // Observing the pager's own scroll position does work: when a
                // fling settles on the last page and the gesture was still moving
                // forward, the reader has clearly asked for more.
                .pointerInput(pageCount, hasPrevious, hasNext) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var lastX = down.position.x
                        var totalDx = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            totalDx += change.position.x - lastX
                            lastX = change.position.x
                            if (!change.pressed) break
                        }
                        // `totalDx` is positive for a rightward swipe.
                        val threshold = 60f
                        val atFirst = pagerState.currentPage == 0
                        val atLast = pagerState.currentPage == pageCount - 1
                        if (totalDx > threshold && atFirst && hasPrevious) {
                            onPreviousChapter()
                        } else if (totalDx < -threshold && atLast && hasNext) {
                            onNextChapter()
                        }
                    }
                },
            pageSpacing = 0.dp,
            userScrollEnabled = true
        ) { page ->
            val items = pages.getOrElse(page) { emptyList() }

            val slide = pagerState.currentPage - page +
                pagerState.currentPageOffsetFraction

            PageSurface(
                mode = settings.pageTurn,
                slide = slide,
                settings = settings,
                palette = palette,
                title = if (page == 0) chapterTitle else null,
                items = items
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shownPage = (pagerState.currentPage + 1)
                .coerceIn(1, pageCount)
            Text(
                text = "$shownPage / $pageCount",
                color = palette.secondary,
                fontSize = 11.sp,
                letterSpacing = (-0.011).em
            )
            Text(
                text = "左右滑动翻页",
                color = palette.secondary,
                fontSize = 11.sp,
                letterSpacing = (-0.011).em
            )
        }
    }
}

/**
 * One page with the visual treatment for the active mode.
 *
 * [slide] is how far this page is from the settled position: 0 when current,
 * positive when it sits to the right, negative to the left. Deriving the effect
 * from the pager's own offset makes it track the finger instead of snapping once
 * the gesture ends, which is what made an earlier version feel abrupt.
 */
@Composable
private fun PageSurface(
    mode: PageTurnMode,
    slide: Float,
    settings: ReaderSettings,
    palette: ReadingPalette,
    title: String?,
    items: List<String>
) {
    // Smooth the offset so a fling does not produce a visible jump.
    val eased by animateFloatAsState(
        targetValue = slide,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "slide"
    )

    // SLIDE needs no transform of its own: the pager already translates each
    // page. The offset is still eased so a fling does not read as a jump.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = settings.marginDp.dp)
                // Structural clearance for the reader's own top and bottom bars.
                // These are not spacing-scale values: they reserve room for chrome
                // that floats over the page, so they stay literals.
                .padding(top = 56.dp, bottom = 44.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    color = palette.text,
                    fontSize = (settings.fontSizeSp + 3).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.022).em,
                    lineHeight = ((settings.fontSizeSp + 3) * settings.lineHeightMultiplier).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.md)
                )
            }

            items.forEach { paragraph ->
                Text(
                    text = paragraph,
                    color = palette.text,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                    letterSpacing = (-0.011).em,
                    softWrap = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (settings.fontSizeSp * 0.5f).dp)
                )
            }
        }
    }
}
