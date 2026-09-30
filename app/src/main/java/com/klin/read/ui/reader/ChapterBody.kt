package com.klin.read.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klin.read.data.ReaderSettings

/**
 * Body of a chapter, rendered as scrolling paragraphs.
 *
 * The text is split into paragraph blocks and rendered lazily rather than as one
 * enormous `Text`. A single chapter of a long novel can run to hundreds of
 * kilobytes; one node that large makes layout and scrolling stutter, and a source
 * file whose lines are not hard-wrapped became one block that scrolled awkwardly.
 */
@Composable
fun ChapterBody(
    body: String,
    settings: ReaderSettings,
    chapterTitle: String,
    palette: ReadingPalette,
    startParagraph: Int,
    hasNext: Boolean,
    onParagraphVisible: (Int) -> Unit,
    onReachedEnd: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToParagraph: Int? = null
) {
    val paragraphs = remember(body) { splitParagraphs(body) }
    val listState = rememberLazyListState()

    LaunchedEffect(paragraphs, scrollToParagraph) {
        if (scrollToParagraph != null && scrollToParagraph in paragraphs.indices) {
            listState.scrollToItem(scrollToParagraph)
        }
    }

    // Report the first visible paragraph so the reading position survives leaving
    // the screen. Fires on every scroll frame, so the callback only touches
    // in-memory state.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { onParagraphVisible(it) }
    }

    // Guide the reader into the next chapter when they reach the bottom: a small
    // trailing item is shown, and scrolling onto it advances.
    //
    // Requiring an explicit tap was worse -- in a continuous scroll there is no
    // obvious moment where the chapter is "finished", so the reader was left
    // staring at the end of the text.
    if (hasNext) {
        LaunchedEffect(listState, paragraphs.size) {
            snapshotFlow {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                // The trailing prompt sits one item past the last paragraph.
                last >= paragraphs.size
            }.collect { atEnd ->
                if (atEnd) onReachedEnd()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = settings.marginDp.dp,
            end = settings.marginDp.dp,
            top = 64.dp,
            bottom = 96.dp
        )
    ) {
        item(key = "chapter-title") {
            Text(
                text = chapterTitle,
                color = palette.text,
                fontSize = (settings.fontSizeSp + 4).sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = ((settings.fontSizeSp + 4) * settings.lineHeightMultiplier).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            )
        }

        items(paragraphs.size, key = { it }) { index ->
            Text(
                text = paragraphs[index],
                color = palette.text,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                // Stated explicitly even though it is the default: wrapping is the
                // behaviour reported as missing, and the LazyColumn supplies the
                // width after the horizontal padding above.
                softWrap = true,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = (settings.fontSizeSp * 0.55f).dp)
            )
        }

        // Trailing marker. Reaching it advances to the next chapter, and it also
        // tells the reader that this is the end of the chapter rather than the
        // end of the book.
        if (hasNext) {
            item(key = "chapter-end") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "继续下滑进入下一章",
                        color = palette.secondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Splits chapter text into paragraph blocks.
 *
 * Sources vary: some put blank lines between paragraphs, some a single newline
 * per line, and some are one continuous run with no breaks at all.
 *
 * Instead of a fixed character count, an over-long run is broken at sentence
 * punctuation. Cutting mid-sentence produces fragments that read as mistakes,
 * while cutting after a full stop keeps every block a whole thought.
 */
internal fun splitParagraphs(body: String): List<String> {
    val raw = body.trim().lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (raw.isEmpty()) return emptyList()

    /** Beyond this a line is treated as an unwrapped run, not a real paragraph. */
    val maxLength = 1200
    /** Target size when breaking a long run at sentence boundaries. */
    val targetLength = 600

    val out = ArrayList<String>(raw.size)
    raw.forEach { line ->
        if (line.length <= maxLength) {
            out += line
        } else {
            out += splitAtSentences(line, targetLength)
        }
    }
    return out
}

/**
 * Breaks [text] into blocks of roughly [target] characters, preferring to end
 * each block after sentence-final punctuation.
 */
private fun splitAtSentences(text: String, target: Int): List<String> {
    val enders = charArrayOf('\u3002', '\uFF01', '\uFF1F', '\u2026', '\u201D', '\u300D', '\u300F', '\n')
    val blocks = ArrayList<String>()
    var start = 0

    while (start < text.length) {
        val limit = (start + target).coerceAtMost(text.length)
        if (limit == text.length) {
            blocks += text.substring(start)
            break
        }

        // Look back from the limit for the nearest sentence end. If none is found
        // in the window, fall back to the limit so progress is still guaranteed.
        var cut = -1
        val floor = (limit - target / 2).coerceAtLeast(start + 1)
        for (i in limit downTo floor) {
            if (text[i - 1] in enders) {
                cut = i
                break
            }
        }
        if (cut <= start) cut = limit

        blocks += text.substring(start, cut)
        start = cut
    }
    return blocks
}
