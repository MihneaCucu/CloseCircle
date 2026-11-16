package com.closecircle.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecircle.app.data.Member
import com.closecircle.app.domain.Freshness
import com.closecircle.app.domain.freshnessLabel
import com.closecircle.app.domain.freshnessOf
import com.closecircle.app.ui.theme.Cc
import com.closecircle.app.ui.theme.CcColors

/** Dot and text colours for a freshness state (green / yellow / grey). */
data class FreshColors(val dot: Color, val fg: Color)

fun CcColors.fresh(f: Freshness) = when (f) {
    Freshness.RECENT -> FreshColors(green, greenFg)
    Freshness.LAST_KNOWN -> FreshColors(yellow, yellowFg)
    Freshness.UNKNOWN -> FreshColors(grey, greyFg)
}

@Composable
fun CcCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Cc.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = c.surface,
        shadowElevation = if (Cc.elder) 0.dp else 1.dp,
        border = if (Cc.elder) BorderStroke(2.dp, c.text) else null,
        content = content,
    )
}

@Composable
fun Cap(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Cc.colors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun FreshChip(minutesAgo: Int?, modifier: Modifier = Modifier) {
    val c = Cc.colors
    val f = freshnessOf(minutesAgo)
    val col = c.fresh(f)
    Text(
        freshnessLabel(minutesAgo),
        modifier = modifier
            .clip(CircleShape)
            .background(lerp(c.surface, col.dot, 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = col.fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 13.sp,
    )
}

/** Filled tint + thin border in the status colour; last known keeps a dashed border. */
@Composable
fun MemberAvatar(m: Member, size: Dp = 48.dp) {
    val c = Cc.colors
    val f = freshnessOf(m.minutesAgo)
    val dot = c.fresh(f).dot
    val fg = c.fresh(f).fg
    val dashed = f == Freshness.LAST_KNOWN
    val fill = lerp(c.surface, dot, 0.16f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawCircle(
                    color = dot,
                    radius = (this.size.minDimension - w) / 2,
                    style = Stroke(width = w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(m.initials, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
fun CcSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    val c = Cc.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.accent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = c.off, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Pill button; at least 56 dp tall in elderly mode. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Cc.colors.accent,
    content: Color = Cc.colors.onAccent,
    enabled: Boolean = true,
    border: BorderStroke? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (Cc.elder) 56.dp else 48.dp),
        enabled = enabled,
        shape = CircleShape,
        border = border,
        colors = ButtonDefaults.buttonColors(container, content, container.copy(alpha = 0.4f), content.copy(alpha = 0.7f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Placeholder map drawn with the mockup palette; replaced by MapLibre later. */
@Composable
fun MapCanvas(modifier: Modifier = Modifier) {
    val c = Cc.colors
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.mapBg)
        drawRect(c.park, Offset(size.width * 0.58f, size.height * 0.04f), Size(size.width * 0.36f, size.height * 0.22f))
        drawRect(c.park, Offset(size.width * 0.04f, size.height * 0.62f), Size(size.width * 0.26f, size.height * 0.30f))
        drawLine(c.road, Offset(size.width * 0.30f, 0f), Offset(size.width * 0.30f, size.height), 8.dp.toPx())
        drawLine(c.road, Offset(0f, size.height * 0.40f), Offset(size.width, size.height * 0.40f), 6.dp.toPx())
        drawLine(c.road, Offset(0f, size.height * 0.70f), Offset(size.width, size.height * 0.70f), 5.dp.toPx())
        drawLine(c.road, Offset(size.width * 0.78f, 0f), Offset(size.width * 0.78f, size.height), 5.dp.toPx())
    }
}

/** Dashed teal circle used for zones. */
@Composable
fun ZoneCircle(size: Dp, modifier: Modifier = Modifier, center: Boolean = false) {
    val c = Cc.colors
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(c.accent.copy(alpha = 0.10f))
                drawCircle(c.accent, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (center) Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
fun ZonesTabIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        drawCircle(color, radius = 9.dp.toPx(), style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        drawCircle(color, radius = 2.2.dp.toPx())
    }
}

@Composable
fun Modifier.outlined(selected: Boolean): Modifier {
    val c = Cc.colors
    return this
        .clip(CircleShape)
        .background(if (selected) c.accentSoft else c.surface)
        .border(1.dp, if (selected) c.accent else c.line, CircleShape)
}

@Composable
fun GearButton(onClick: () -> Unit) {
    val c = Cc.colors
    Surface(shape = CircleShape, color = c.surface, shadowElevation = 2.dp, modifier = Modifier.size(44.dp)) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Settings, contentDescription = "Setări", tint = c.text)
        }
    }
}
