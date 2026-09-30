package com.klin.read.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klin.read.R
import kotlinx.coroutines.delay

/**
 * Launch screen: icon, app name, a progress bar and the line of the day.
 *
 * The bar reflects real startup progress rather than running on a timer, so it
 * does not sit full while the app is still working. The quote is chosen once per
 * day by [DailyQuote].
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    /** Reports how far startup has actually progressed, 0f..1f. */
    progressProvider: () -> Float = { 1f }
) {
    val context = LocalContext.current

    var caption by remember { mutableStateOf("") }
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }
    var targetProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        caption = DailyQuote.today(context)

        iconVisible = true
        titleVisible = true
        delay(260)
        barVisible = true

        // Hold the screen long enough to actually read the line underneath the
        // bar. The previous 900ms passed before the caption could be taken in,
        // which made the whole screen feel like a flicker.
        val minimumVisibleMs = 1_800L

        val started = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            val real = progressProvider().coerceIn(0f, 1f)
            targetProgress = maxOf(real, (elapsed / minimumVisibleMs.toFloat()).coerceAtMost(0.95f))
            if (elapsed >= minimumVisibleMs && real >= 1f) break
            if (elapsed > 3_000) break
            delay(16)
        }
        targetProgress = 1f
        delay(300)
        onFinished()
    }

    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(560),
        label = "icon"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(620),
        label = "title"
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(420),
        label = "bar"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(180),
        label = "progress"
    )

    val canvas = Color(0xFF141414)
    val ink = Color(0xFFEDEBE7)
    val faint = Color(0xFF7C7872)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // The icon is drawn as a circle, with no backing plate.
            //
            // An earlier version put a white disc behind the artwork to make a
            // round icon. The artwork already carries its own background, so that
            // showed as a white ring around the image. Clipping alone is enough.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(112.dp)
                    .alpha(iconAlpha)
                    .clip(CircleShape)
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = "KlinRead",
                color = ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(titleAlpha)
            )

            Spacer(Modifier.height(34.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ink.copy(alpha = 0.14f))
                    .alpha(barAlpha)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.dp)
                        .background(ink.copy(alpha = 0.75f))
                )
            }

            Spacer(Modifier.height(18.dp))

            // Line of the day: smaller and dimmer than the title.
            Text(
                text = caption,
                color = faint,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(barAlpha)
            )
        }
    }
}
