package com.klin.read.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klin.read.R
import com.klin.read.ui.design.Hairline
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Panel
import com.klin.read.ui.design.PrimaryButton
import com.klin.read.ui.design.ScreenTitle
import com.klin.read.ui.design.SectionLabel
import com.klin.read.ui.design.Space

/** Credits page, carrying the donation entry point. */
@Composable
fun AboutScreen() {
    val c = LocalColors.current
    val context = LocalContext.current
    var showDonate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg,
            end = Space.lg,
            top = Space.xl,
            bottom = 140.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item {
            ScreenTitle("作者")
            Text(
                text = "这个阅读器背后的人和事",
                color = c.inkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item {
            SectionLabel("关于")
            Panel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                CreditRow(
                    avatar = { AuthorAvatar() },
                    name = "Klin",
                    role = "作者",
                    note = "贡献了收款码",
                    hint = "点击进入主页",
                    onClick = { openUrl(context, "https://qm.qq.com/q/Gha6BHZKoi") }
                )
                Hairline()
                CreditRow(
                    avatar = { InitialAvatar("DS") },
                    name = "DeepSeek Harness",
                    role = "开发工具",
                    note = "全部代码编写",
                    hint = "点击访问官网",
                    onClick = { openUrl(context, "https://www.deepseek.com") }
                )
            }
        }

        item {
            SectionLabel("支持")
            Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = "如果这个项目帮到了你，可以请作者喝一杯。完全自愿，不打赏也不影响任何功能。",
                    color = c.inkMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(Space.md))
                PrimaryButton(
                    text = "捐赠",
                    onClick = { showDonate = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDonate) {
        AlertDialog(
            onDismissRequest = { showDonate = false },
            title = { Text("捐赠", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_donate),
                        contentDescription = "收款码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text = "扫码请作者喝一杯，完全自愿。",
                        color = c.inkMuted,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDonate = false }) {
                    Text("关闭", color = c.accent, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}

@Composable
private fun CreditRow(
    avatar: @Composable () -> Unit,
    name: String,
    role: String,
    note: String,
    hint: String,
    onClick: () -> Unit
) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar()
        Column(Modifier.weight(1f).padding(start = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = role,
                    color = c.inkFaint,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }
            Text(
                text = note,
                color = c.inkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                text = hint,
                color = c.accent,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        // Chevron, so the row reads as tappable.
        Text("›", color = c.inkFaint, fontSize = 20.sp)
    }
}

/** Opens [url] in whatever browser or app handles it. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun AuthorAvatar() {
    Image(
        painter = painterResource(R.drawable.author_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
    )
}

/** Neutral placeholder for a contributor with no picture on file. */
@Composable
private fun InitialAvatar(initials: String) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.surfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = c.inkMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
