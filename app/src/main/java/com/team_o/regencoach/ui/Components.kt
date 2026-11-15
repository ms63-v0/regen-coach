package com.team_o.regencoach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team_o.regencoach.domain.BrakeState
import com.team_o.regencoach.domain.GuidePhase
import com.team_o.regencoach.domain.GuideState
import com.team_o.regencoach.ui.theme.LocalPds
import com.team_o.regencoach.ui.theme.PdsRadius
import com.team_o.regencoach.ui.theme.PdsSpace
import com.team_o.regencoach.ui.theme.PdsTouch

@Composable
fun PdsText(
    text: String,
    size: TextUnit = 18.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPds.current.ink,
    modifier: Modifier = Modifier,
) {
    Text(text = text, fontSize = size, fontWeight = weight, color = color, modifier = modifier)
}

/** 회생·마찰 비중을 보여주는 3색 링 게이지 */
@Composable
fun RegenRing(
    regenFill: Float,
    frictionFill: Float,
    state: BrakeState,
    flash: Boolean,
    ringSize: Dp = 300.dp,
    brightness: Float = 1f,
) {
    val c = LocalPds.current
    val regen by animateFloatAsState(regenFill, tween(150), label = "regen")
    val friction by animateFloatAsState(frictionFill, tween(150), label = "friction")
    val regenColor = c.regen.copy(alpha = brightness)
    val frictionColor = c.friction.copy(alpha = brightness)
    val showWarning = state == BrakeState.FRICTION_ONLY || state == BrakeState.REGEN_LIMITED

    Canvas(modifier = Modifier.size(ringSize)) {
        val stroke = size.minDimension * 0.075f
        val r = size.minDimension / 2f - stroke * 1.6f
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(r * 2, r * 2)

        // 눈금
        val tickR = r + stroke * 1.15f
        for (i in 0 until 72) {
            rotate(i * 5f) {
                drawLine(
                    color = if (flash) c.accent else c.line,
                    start = Offset(center.x, center.y - tickR),
                    end = Offset(center.x, center.y - tickR - stroke * 0.35f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
        // 트랙
        drawArc(c.surface300, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        // 회생(파랑) → 마찰(주황) 순서로 시계방향
        val regenSweep = 360f * regen.coerceIn(0f, 1f)
        val frictionSweep = 360f * friction.coerceIn(0f, 1f - regen.coerceIn(0f, 1f))
        if (regenSweep > 0.5f) {
            drawArc(regenColor, -90f, regenSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        }
        if (frictionSweep > 0.5f) {
            drawArc(frictionColor, -90f + regenSweep, frictionSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        }
        // 안쪽 원
        drawCircle(c.surface100, radius = r - stroke * 0.9f)
        // 가운데 아이콘
        if (showWarning) drawWarning(c.inkMuted, r * 0.32f) else drawBolt(if (regen > 0f) c.regen else c.inkDisabled, r * 0.36f)
    }
}

private fun DrawScope.drawBolt(color: Color, half: Float) {
    // 24 단위 bolt 아이콘: M13 2 L4.5 13.5 H11 L10 22 L18.5 10.5 H12 Z
    val s = half * 2f / 24f
    val ox = center.x - 12f * s
    val oy = center.y - 12f * s
    val p = Path().apply {
        moveTo(ox + 13f * s, oy + 2f * s)
        lineTo(ox + 4.5f * s, oy + 13.5f * s)
        lineTo(ox + 11f * s, oy + 13.5f * s)
        lineTo(ox + 10f * s, oy + 22f * s)
        lineTo(ox + 18.5f * s, oy + 10.5f * s)
        lineTo(ox + 12f * s, oy + 10.5f * s)
        close()
    }
    drawPath(p, color, style = Stroke(width = 2.2f * s, join = StrokeJoin.Round))
}

private fun DrawScope.drawWarning(color: Color, half: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y - half)
        lineTo(center.x + half, center.y + half * 0.8f)
        lineTo(center.x - half, center.y + half * 0.8f)
        close()
    }
    drawPath(p, color, style = Stroke(width = half * 0.13f, join = StrokeJoin.Round))
    drawLine(color, Offset(center.x, center.y - half * 0.35f), Offset(center.x, center.y + half * 0.2f), half * 0.13f, StrokeCap.Round)
    drawCircle(color, half * 0.08f, Offset(center.x, center.y + half * 0.5f))
}

/** 예측 감속 안내 바 */
@Composable
fun GuideBar(guide: GuideState) {
    val c = LocalPds.current
    val barColor = when (guide.phase) {
        GuidePhase.LATE -> c.friction
        GuidePhase.RELEASE_NOW, GuidePhase.DECELERATING -> c.regen
        else -> c.regen.copy(alpha = 0.55f)
    }
    val visible = guide.phase != GuidePhase.HIDDEN
    val progress = when (guide.phase) {
        GuidePhase.RELEASE_NOW, GuidePhase.LATE, GuidePhase.DECELERATING -> 1f
        else -> guide.progress
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(PdsTouch.min)
            .clip(RoundedCornerShape(PdsRadius.md))
            .background(c.surface100)
            .padding(horizontal = PdsSpace.s4),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (visible) barColor else c.surface300),
        )
        Spacer(Modifier.width(PdsSpace.s4))
        Canvas(Modifier.weight(1f).height(14.dp)) {
            val segments = 24
            val gap = 4f
            val w = (size.width - gap * (segments - 1)) / segments
            val filled = if (visible) (progress * segments).toInt() else 0
            for (i in 0 until segments) {
                drawRoundRect(
                    color = if (i < filled) barColor else c.surface300,
                    topLeft = Offset(i * (w + gap), 0f),
                    size = Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                )
            }
        }
        Spacer(Modifier.width(PdsSpace.s4))
        PdsText(
            text = guide.label?.substringBefore(" ·") ?: "경로 없음",
            size = 18.sp,
            weight = FontWeight.SemiBold,
            color = if (visible) c.ink else c.inkMuted,
        )
    }
}

/** 시연 콘솔 버튼 (주행 중 최소 터치 76dp) */
@Composable
fun ConsoleButton(
    label: String,
    sub: String? = null,
    selected: Boolean = false,
    accent: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalPds.current
    val bg = when {
        selected -> accent ?: c.accent
        else -> c.surface100
    }
    val fg = if (selected) Color.White else c.ink
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .defaultMinSize(minHeight = PdsTouch.min)
            .clip(RoundedCornerShape(PdsRadius.sm))
            .background(bg)
            .border(1.dp, if (selected) bg else c.line, RoundedCornerShape(PdsRadius.sm))
            .clickable(onClick = onClick)
            .padding(PdsSpace.s2),
    ) {
        PdsText(label, 18.sp, FontWeight.SemiBold, fg)
        if (sub != null) PdsText(sub, 11.sp, FontWeight.Normal, if (selected) Color.White.copy(alpha = 0.85f) else c.inkMuted)
    }
}

@Composable
fun Chip(text: String, fg: Color, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = PdsSpace.s3, vertical = PdsSpace.s1 + 2.dp),
    ) {
        PdsText(text, 14.sp, FontWeight.SemiBold, fg)
    }
}
