package com.team_o.regencoach.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pleos Design System v2 토큰 (Day / Night).
 * 웹 px 값은 Android dp/sp 로 그대로 옮김.
 */
@Immutable
data class PdsColors(
    val screen: Color,
    val win: Color,
    val surface100: Color,
    val surface200: Color,
    val surface300: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkDisabled: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val positive: Color,
    val positiveSoft: Color,
    val caution: Color,
    val cautionSoft: Color,
    val critical: Color,
    // 리젠코치 전용 상태 색
    val regen: Color,
    val friction: Color,
    val idle: Color,
    val isNight: Boolean,
)

val DayColors = PdsColors(
    screen = Color(0xFFEBECEF),
    win = Color(0xFFF7F7F8),
    surface100 = Color(0xFFFFFFFF),
    surface200 = Color(0xFFEEF0F3),
    surface300 = Color(0xFFE2E5EA),
    line = Color(0xFFE3E5E9),
    ink = Color(0xFF15181C),
    inkMuted = Color(0xFF59616D),
    inkDisabled = Color(0xFFA7AEB8),
    accent = Color(0xFF3A52E0),
    accentSoft = Color(0xFFE7EAFD),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF19723F),
    positiveSoft = Color(0xFFE2F4E9),
    caution = Color(0xFF9A5600),
    cautionSoft = Color(0xFFFDF1DC),
    critical = Color(0xFFC02D22),
    regen = Color(0xFF3B6FE0),
    friction = Color(0xFFF08A3C),
    idle = Color(0xFFC9CBD1),
    isNight = false,
)

val NightColors = PdsColors(
    screen = Color(0xFF0A0C0F),
    win = Color(0xFF15181D),
    surface100 = Color(0xFF1E2228),
    surface200 = Color(0xFF272C34),
    surface300 = Color(0xFF323843),
    line = Color(0xFF2A2F37),
    ink = Color(0xFFF2F4F7),
    inkMuted = Color(0xFFA9B1BD),
    inkDisabled = Color(0xFF5D6571),
    accent = Color(0xFF8596FF),
    accentSoft = Color(0xFF1C2350),
    onAccent = Color(0xFF0A0F2E),
    positive = Color(0xFF5CD394),
    positiveSoft = Color(0xFF0F2A1D),
    caution = Color(0xFFFFB547),
    cautionSoft = Color(0xFF33240A),
    critical = Color(0xFFFF7A6B),
    regen = Color(0xFF7FA2FF),
    friction = Color(0xFFFFA25E),
    idle = Color(0xFF4A505A),
    isNight = true,
)

object PdsSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
}

object PdsRadius {
    val sm = 14.dp
    val md = 20.dp
    val lg = 28.dp
}

object PdsTouch {
    /** 주행 중 일반 액션 최소 크기 */
    val min = 76.dp
    /** 주행 중 주요 액션 최소 크기 */
    val large = 96.dp
}

val LocalPds = staticCompositionLocalOf { DayColors }

@Composable
fun PdsTheme(night: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPds provides if (night) NightColors else DayColors) {
        content()
    }
}
