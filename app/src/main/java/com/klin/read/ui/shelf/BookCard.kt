package com.klin.read.ui.shelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.klin.read.data.BookEntity
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Motion
import com.klin.read.ui.design.Radius
import com.klin.read.ui.design.Size
import com.klin.read.ui.design.Space
import java.io.File

/**
 * A book in the shelf grid.
 *
 * The shelf used to be a vertical list of wide rows. That works for a file
 * manager but not for a bookshelf: at three books per screen the reader scrolls
 * constantly, and a 62x88 cover is too small to recognise a book by. A grid shows
 * nine at once and lets the cover -- the thing a reader actually recognises -- be
 * the dominant element.
 *
 * Anatomy, top to bottom:
 *
 *   - the cover at a fixed 2:3 ratio, which is the standard book proportion, so
 *     covers never look stretched regardless of column width;
 *   - a reading-progress bar drawn over the cover's bottom edge, so progress is
 *     visible without adding a row of chrome;
 *   - the title, capped at two lines;
 *   - the format and size in muted text.
 */
@Composable
fun BookCard(
    book: BookEntity,
    progress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The skill's active state: the whole card pulls back slightly.
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.pop(),
        label = "cardPress"
    )

    Column(
        modifier = modifier
            .scale(scale)
            // combinedClickable rather than clickable: the long press opens the
            // actions sheet, and two separate pointerInput modifiers on one node
            // is the pattern that already broke the slider's gesture handling.
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 2:3 is the standard book cover ratio. Fixing it here means the
                // grid can change column count without distorting artwork.
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(Radius.small),
                    ambientColor = c.glassShadow,
                    spotColor = c.glassShadow
                )
                .clip(RoundedCornerShape(Radius.small))
                .background(c.fieldFill)
                .border(
                    width = 0.5.dp,
                    color = c.glassBorder,
                    shape = RoundedCornerShape(Radius.small)
                )
        ) {
            val coverPath = book.coverPath
            if (coverPath != null) {
                AsyncImage(
                    model = File(coverPath),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GeneratedCover(title = book.title, modifier = Modifier.fillMaxSize())
            }

            /*
             * Progress over the cover's bottom edge.
             *
             * `fillMaxHeight` rather than `fillMaxSize` on the inner bar. The inner
             * box had BOTH `.fillMaxWidth(progress)` and `.fillMaxSize()`, and
             * `fillMaxSize` sets width as well as height -- so it overrode the
             * fraction and the bar rendered full width for any progress above zero.
             * That is why the shelf showed either nothing or a completely full bar:
             * the ratio was being computed correctly and then thrown away at draw
             * time.
             */
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(Size.progressBarThin)
                        .background(c.glassShadow)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(c.accent)
                    )
                }
            }

            // "读完" marker, top-right of the cover.
            if (book.isFinished) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.xs)
                        .clip(RoundedCornerShape(Radius.small))
                        .background(c.accent)
                        .padding(horizontal = Space.xs, vertical = 2.dp)
                ) {
                    Text(
                        text = "读完",
                        color = c.accentInk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.011).em
                    )
                }
            }
        }

        Text(
            text = book.title,
            color = c.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 8dp below the cover keeps the block on the grid; the text block is
            // two lines tall at most so the grid rows stay even.
            modifier = Modifier.padding(top = Space.sm)
        )

        BookMetaLine(book)
    }
}

/**
 * The metadata line under a title.
 *
 * Kept separate so the grid can drop it at very small column widths without
 * touching the cover/title layout.
 */
@Composable
private fun BookMetaLine(book: BookEntity) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = book.format,
            color = c.inkFaint,
            fontSize = 11.sp,
            letterSpacing = (-0.011).em,
            maxLines = 1
        )
        if (book.charCount > 0) {
            Text(
                text = "·",
                color = c.inkFaint,
                fontSize = 11.sp
            )
            Text(
                text = formatCharCount(book.charCount),
                color = c.inkFaint,
                fontSize = 11.sp,
                letterSpacing = (-0.011).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCharCount(count: Int): String = when {
    count >= 10_000 -> "${count / 10_000} 万字"
    else -> "$count 字"
}

