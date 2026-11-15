package com.team_o.regencoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.team_o.regencoach.RegenViewModel
import com.team_o.regencoach.data.Gear
import com.team_o.regencoach.data.PedalInput
import com.team_o.regencoach.data.VehicleUi
import com.team_o.regencoach.domain.BrakeState
import com.team_o.regencoach.domain.CoachingState
import com.team_o.regencoach.domain.GuidePhase
import com.team_o.regencoach.domain.SuppressReason
import com.team_o.regencoach.domain.TripReport
import com.team_o.regencoach.ui.theme.LocalPds
import com.team_o.regencoach.ui.theme.PdsRadius
import com.team_o.regencoach.ui.theme.PdsSpace
import com.team_o.regencoach.ui.theme.PdsTheme
import java.util.Locale

@Composable
fun RegenCoachApp(vm: RegenViewModel) {
    val coaching by vm.coaching.collectAsStateWithLifecycle()
    val sdk by vm.sdkState.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val coachingOn by vm.coachingOn.collectAsStateWithLifecycle()

    PdsTheme(night = coaching.night) {
        val c = LocalPds.current
        Box(
            Modifier
                .fillMaxSize()
                .background(c.win)
                .padding(PdsSpace.s5),
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PdsSpace.s5)) {
                Column(Modifier.weight(1.15f).fillMaxHeight()) {
                    AppBar(coaching, coachingOn, points, vm::toggleCoaching)
                    Spacer(Modifier.height(PdsSpace.s4))
                    when (val s = sdk) {
                        is VehicleUi.Ready -> CoachingPanel(coaching, coachingOn, Modifier.weight(1f))
                        else -> StateView(s, Modifier.weight(1f))
                    }
                }
                SimConsole(vm, Modifier.weight(0.85f).fillMaxHeight())
            }
            report?.let { TripReportOverlay(it, points, vm::dismissReport) }
        }
    }
}

@Composable
private fun AppBar(state: CoachingState, coachingOn: Boolean, points: Int, onToggle: () -> Unit) {
    val c = LocalPds.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF5B6CF0)),
            contentAlignment = Alignment.Center,
        ) { PdsText("⚡", 24.sp, FontWeight.Bold, Color.White) }
        Spacer(Modifier.width(PdsSpace.s3))
        PdsText("리젠코치", 28.sp, FontWeight.Bold)
        Spacer(Modifier.width(PdsSpace.s3))
        when {
            state.gear != Gear.D -> Chip("P 정차", c.inkMuted, c.surface200)
            coachingOn -> Chip("코칭 중", c.accent, c.accentSoft)
            else -> Chip("코칭 꺼짐", c.inkMuted, c.surface200)
        }
        Spacer(Modifier.weight(1f))
        Chip("누적 ${points}P", c.positive, c.positiveSoft)
        Spacer(Modifier.width(PdsSpace.s3))
        ConsoleButton(if (coachingOn) "코칭 끄기" else "코칭 켜기", modifier = Modifier.width(140.dp), onClick = onToggle)
    }
}

@Composable
private fun CoachingPanel(state: CoachingState, coachingOn: Boolean, modifier: Modifier) {
    val c = LocalPds.current
    val brightness = (0.55f + state.ambientBrightness * 0.045f).coerceIn(0.6f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PdsRadius.lg))
            .background(c.surface200)
            .padding(PdsSpace.s5),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val hidden = !coachingOn || state.suppress != SuppressReason.NONE
            if (hidden) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PdsText(
                        when {
                            !coachingOn -> "코칭 화면 꺼짐"
                            state.suppress == SuppressReason.TURN_SIGNAL -> "방향지시등 작동 중"
                            state.suppress == SuppressReason.CRUISE -> "크루즈 주행 중"
                            state.suppress == SuppressReason.STEERING -> "조향 중"
                            else -> "충전 중"
                        },
                        24.sp, FontWeight.SemiBold, c.inkMuted,
                    )
                    Spacer(Modifier.height(PdsSpace.s2))
                    PdsText(
                        if (!coachingOn) "트립 리포트와 포인트는 계속 기록됨" else "코칭 표시를 잠시 멈춤",
                        16.sp, FontWeight.Normal, c.inkMuted,
                    )
                }
            } else {
                RegenRing(
                    regenFill = state.regenFill,
                    frictionFill = state.frictionFill,
                    state = state.brakeState,
                    flash = state.guide.phase == GuidePhase.RELEASE_NOW,
                    brightness = brightness,
                )
            }
        }
        // 주행 중에는 숫자 대신 상태 이름만 표시
        if (coachingOn && state.suppress == SuppressReason.NONE) {
            val (label, color) = when (state.brakeState) {
                BrakeState.REGEN_ONLY -> "회생만" to c.regen
                BrakeState.REGEN_AND_FRICTION -> "회생 + 마찰" to c.friction
                BrakeState.FRICTION_ONLY -> "마찰만" to c.inkMuted
                BrakeState.REGEN_LIMITED -> "회생 제한" to c.inkMuted
                BrakeState.DRIVING -> "주행 중" to c.inkMuted
                BrakeState.PARKED -> "정차" to c.inkMuted
            }
            PdsText(label, 26.sp, FontWeight.Bold, color)
            Spacer(Modifier.height(PdsSpace.s4))
            GuideBar(state.guide)
        }
    }
}

@Composable
private fun StateView(state: VehicleUi<Unit>, modifier: Modifier) {
    val c = LocalPds.current
    val (title, body) = when (state) {
        VehicleUi.Loading -> "차량 연결 중" to "Vehicle SDK 초기화를 기다리는 중"
        VehicleUi.Unsupported -> "지원하지 않는 차량" to "이 차량은 회생제동 신호를 제공하지 않음"
        VehicleUi.PermissionRequired -> "권한 필요" to "CAR_DRIVE · CAR_ENERGY 권한을 허용해 주세요"
        is VehicleUi.Error -> "연결 오류" to "오류 코드 ${state.code ?: "-"} · 다시 시도해 주세요"
        is VehicleUi.Ready -> "" to ""
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(PdsRadius.lg)).background(c.surface200),
    ) {
        PdsText(title, 26.sp, FontWeight.Bold)
        Spacer(Modifier.height(PdsSpace.s2))
        PdsText(body, 16.sp, FontWeight.Normal, c.inkMuted)
    }
}

@Composable
private fun SimConsole(vm: RegenViewModel, modifier: Modifier) {
    val c = LocalPds.current
    val sim = vm.sim
    val pedal by sim.pedal.collectAsStateWithLifecycle()
    val gear by sim.gear.collectAsStateWithLifecycle()
    val turn by sim.turn.collectAsStateWithLifecycle()
    val cruise by sim.cruise.collectAsStateWithLifecycle()
    val night by sim.night.collectAsStateWithLifecycle()
    val full by sim.batteryFull.collectAsStateWithLifecycle()
    val downhill by sim.downhill.collectAsStateWithLifecycle()
    val running by sim.scenarioRunning.collectAsStateWithLifecycle()
    val log by sim.apiLog.collectAsStateWithLifecycle()
    val coaching by vm.coaching.collectAsStateWithLifecycle()

    Column(
        verticalArrangement = Arrangement.spacedBy(PdsSpace.s3),
        modifier = modifier
            .clip(RoundedCornerShape(PdsRadius.lg))
            .background(c.surface200)
            .verticalScroll(rememberScrollState())
            .padding(PdsSpace.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PdsText("시연 콘솔", 20.sp, FontWeight.Bold)
            Spacer(Modifier.width(PdsSpace.s2))
            PdsText("Virtual Console 대체 · 가상 주행 데이터", 13.sp, FontWeight.Normal, c.inkMuted)
        }
        PdsText(
            String.format(Locale.KOREA, "%.0f km/h · SOC %.0f%% · 이번 트립 회생 %.3f kWh", coaching.speedKmh, coaching.socPercent, coaching.tripRegenKwh),
            15.sp, FontWeight.SemiBold, c.inkMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s2)) {
            ConsoleButton("P", "주차·트립 종료", gear == Gear.P, modifier = Modifier.weight(1f)) { sim.setGear(Gear.P) }
            ConsoleButton("D", "주행 시작", gear == Gear.D, modifier = Modifier.weight(1f)) { sim.setGear(Gear.D) }
            ConsoleButton(if (running) "재생 중" else "시나리오", "출퇴근 구간 자동", running, modifier = Modifier.weight(1.3f)) { sim.playScenario() }
        }
        PdsText("페달 (registerBrakePedalStatus)", 13.sp, FontWeight.SemiBold, c.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s2)) {
            ConsoleButton("가속", null, pedal == PedalInput.ACCEL, modifier = Modifier.weight(1f)) { sim.setPedal(PedalInput.ACCEL) }
            ConsoleButton("정속", null, pedal == PedalInput.HOLD, modifier = Modifier.weight(1f)) { sim.setPedal(PedalInput.HOLD) }
            ConsoleButton("발 떼기", "회생", pedal == PedalInput.LIFT, c.regen, Modifier.weight(1f)) { sim.setPedal(PedalInput.LIFT) }
            ConsoleButton("브레이크", "마찰", pedal == PedalInput.BRAKE, c.friction, Modifier.weight(1f)) { sim.setPedal(PedalInput.BRAKE) }
        }
        PdsText("차량 이벤트", 13.sp, FontWeight.SemiBold, c.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s2)) {
            ConsoleButton("방향지시등", "TurnSignal", turn != com.team_o.regencoach.data.TurnSignal.OFF, modifier = Modifier.weight(1f)) { sim.toggleTurn() }
            ConsoleButton("크루즈", "CruiseControl", cruise, modifier = Modifier.weight(1f)) { sim.toggleCruise() }
            ConsoleButton("야간", "LowBeam", night, modifier = Modifier.weight(1f)) { sim.toggleNight() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s2)) {
            ConsoleButton("배터리 가득", "SOC 99%", full, modifier = Modifier.weight(1f)) { sim.toggleBatteryFull() }
            ConsoleButton("내리막 3%", "FusedLocation", downhill, modifier = Modifier.weight(1f)) { sim.toggleDownhill() }
        }
        PdsText("SDK·API 호출 기록", 13.sp, FontWeight.SemiBold, c.inkMuted)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PdsRadius.sm))
                .background(c.surface100)
                .padding(PdsSpace.s3),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (log.isEmpty()) PdsText("기록 없음", 13.sp, FontWeight.Normal, c.inkMuted)
            log.forEach { PdsText(it, 13.sp, FontWeight.Normal, c.inkMuted) }
        }
    }
}

@Composable
private fun TripReportOverlay(r: TripReport, total: Int, onClose: () -> Unit) {
    val c = LocalPds.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x6B14181E))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(PdsRadius.lg))
                .background(c.surface100)
                .clickable(enabled = false) {}
                .padding(PdsSpace.s5),
            verticalArrangement = Arrangement.spacedBy(PdsSpace.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdsText("트립 리포트", 24.sp, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Chip("P 시동 OFF 후", c.positive, c.positiveSoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s3)) {
                Stat("회생량", String.format(Locale.KOREA, "%.2f", r.regenKwh), "kWh", "약 ${r.savedWon}원", Modifier.weight(1f))
                Stat("감속 점수", "${r.decelScore}", "점", r.avgTimingGapM?.let { "안내 대비 ${if (it >= 0) "+" else ""}${it}m" } ?: "안내 지점 없음", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PdsSpace.s3)) {
                Stat("CO₂ 감축", String.format(Locale.KOREA, "%.2f", r.co2Kg), "kg", "전력배출계수 0.4173", Modifier.weight(1f))
                Stat("회생 비율", "${r.regenRatioPercent}", "%", String.format(Locale.KOREA, "주행 %.1f km", r.distanceKm), Modifier.weight(1f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PdsRadius.md))
                    .background(c.positive)
                    .padding(PdsSpace.s4),
            ) {
                PdsText(if (r.pointsBlockedReason == null) "+${r.points} P" else "적립 제외", 30.sp, FontWeight.Bold, Color.White)
                Spacer(Modifier.weight(1f))
                PdsText(r.pointsBlockedReason ?: "누적 ${total}P", 16.sp, FontWeight.SemiBold, Color.White)
            }
            ConsoleButton("확인", modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

@Composable
private fun Stat(title: String, value: String, unit: String, sub: String, modifier: Modifier) {
    val c = LocalPds.current
    Column(
        modifier
            .clip(RoundedCornerShape(PdsRadius.md))
            .background(c.surface200)
            .padding(PdsSpace.s4),
    ) {
        PdsText(title, 15.sp, FontWeight.SemiBold, c.inkMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            PdsText(value, 40.sp, FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            PdsText(unit, 18.sp, FontWeight.SemiBold, c.inkMuted, Modifier.padding(bottom = 6.dp))
        }
        PdsText(sub, 14.sp, FontWeight.Normal, c.inkMuted)
    }
}
