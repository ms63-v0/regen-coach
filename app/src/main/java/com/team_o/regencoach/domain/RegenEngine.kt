package com.team_o.regencoach.domain

import com.team_o.regencoach.data.DriveMode
import com.team_o.regencoach.data.Gear
import com.team_o.regencoach.data.TurnSignal
import com.team_o.regencoach.data.VehicleSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 지금 걸린 제동의 종류 */
enum class BrakeState {
    /** 정차 또는 P단 */
    PARKED,
    /** 가속·정속 주행 (감속 아님) */
    DRIVING,
    /** 감속 중 · 페달 false · 회생 신호 있음 */
    REGEN_ONLY,
    /** 감속 중 · 페달 true · 회생 신호 있음 */
    REGEN_AND_FRICTION,
    /** 감속 중 · 페달 true · 회생 신호 없음 */
    FRICTION_ONLY,
    /** 감속 중 · 페달 false · 회생 신호 없음 (고충전·저속 등 회생 제한) */
    REGEN_LIMITED,
}

enum class SuppressReason { NONE, TURN_SIGNAL, CRUISE, STEERING, CHARGING }

enum class GuidePhase { HIDDEN, APPROACH, RELEASE_NOW, LATE, DECELERATING }

data class GuideState(
    val phase: GuidePhase = GuidePhase.HIDDEN,
    /** 하단 진행 바 채움 (0~1) */
    val progress: Float = 0f,
    val distanceM: Float = 0f,
    val triggerM: Float = 0f,
    val label: String? = null,
    /** 남은 거리만으로 회생 단독 감속이 가능한지 */
    val regenOnlyPossible: Boolean = true,
)

data class CoachingState(
    val brakeState: BrakeState = BrakeState.PARKED,
    val suppress: SuppressReason = SuppressReason.NONE,
    /** 링 중 파란(회생) 구간 비율 0~1 */
    val regenFill: Float = 0f,
    /** 링 중 주황(마찰) 구간 비율 0~1 */
    val frictionFill: Float = 0f,
    val guide: GuideState = GuideState(),
    val speedKmh: Float = 0f,
    val night: Boolean = false,
    val ambientBrightness: Int = 8,
    val gear: Gear = Gear.P,
    val socPercent: Float = 0f,
    /** 현재 트립 누적 회생량 (kWh) */
    val tripRegenKwh: Float = 0f,
)

data class TripReport(
    val distanceKm: Float,
    val regenKwh: Float,
    val savedWon: Int,
    val co2Kg: Float,
    /** 감속 에너지 중 회생으로 회수한 비율 (%) */
    val regenRatioPercent: Int,
    /** 감속 점수 (100점 만점) */
    val decelScore: Int,
    /** 안내 시점 대비 감속 시작 거리 차이 평균 (m, 음수 = 늦음) */
    val avgTimingGapM: Int?,
    val points: Int,
    val pointsBlockedReason: String?,
)

/**
 * 차량 신호로 제동 상태를 판정하고, 회생량·감속 점수를 계산하는 온디바이스 엔진.
 * 기획서 5. 개발 계획의 판정 방식과 산출식을 그대로 구현했다.
 */
class RegenEngine(
    /** 차종 테이블 값 (공차중량 kg). Vehicle SDK 는 제원을 제공하지 않아 앱 내장 표로 관리 */
    private val vehicleMassKg: Float = 2_000f,
    /** 차종별 회생 계수 (실측 후 확정) */
    private val regenEfficiency: Float = 0.65f,
) {
    companion object {
        const val REGEN_EFF_THRESHOLD = 50f   // 순간 전비 회생 신호 임계 (km/kWh)
        const val DECEL_THRESHOLD = 0.3f      // 감속 판정 (m/s²)
        const val COMFORT_DECEL = 1.0f        // a_c, 약한 제동 구간 하한
        const val MIN_DECEL = 0.4f            // a_min
        const val REACTION_S = 1.5f           // t_r
        const val PREVIEW_S = 4f              // t_pre
        const val STEERING_LIMIT_DEG = 90f
        const val WON_PER_KWH = 295f          // 공공 완속 충전 요금
        const val CO2_PER_KWH = 0.4173f       // 국가 전력배출계수
        const val POINTS_PER_KWH = 10f        // 앱 자체 설계 기준
        const val G = 9.81f
    }

    private var lastTs = 0L
    private var vSmoothMs = 0f
    private var aSmooth = 0f
    private var lastGear = Gear.P

    // 트립 누적
    private var regenJ = 0.0
    private var regenRawJ = 0.0
    private var frictionJ = 0.0
    private var tripStartKm = 0f
    private var lastTripKm = 0f
    private val timingScores = mutableListOf<Float>()
    private val timingGaps = mutableListOf<Float>()

    // 예측 안내
    private var guideLabel: String? = null
    private var releaseShownAt = 0L
    private var scoredThisPoint = false

    var cumulativePoints: Int = 0
        private set

    /** 새 스냅샷을 반영하고 화면 상태를 돌려준다. P단 전환 시 트립 리포트도 함께 반환. */
    fun update(s: VehicleSnapshot): Pair<CoachingState, TripReport?> {
        val dt = if (lastTs == 0L) 0.1f else ((s.timestampMs - lastTs) / 1000f).coerceIn(0.02f, 1f)
        lastTs = s.timestampMs

        // 트립 시작 / 종료
        var report: TripReport? = null
        if (lastGear != Gear.D && s.gear == Gear.D) resetTrip(s.tripDistanceKm)
        if (lastGear == Gear.D && s.gear == Gear.P) report = buildReport()
        lastGear = s.gear
        lastTripKm = s.tripDistanceKm

        // 속도: 순간 주행거리 ÷ Δt, 이동평균
        val vRaw = s.tripInstantDistanceKm * 1000f / dt
        val vPrev = vSmoothMs
        vSmoothMs = vSmoothMs * 0.6f + vRaw * 0.4f
        if (s.gear != Gear.D) vSmoothMs = 0f
        val a = (vSmoothMs - vPrev) / dt
        aSmooth = aSmooth * 0.5f + a * 0.5f

        val suppress = when {
            s.charging -> SuppressReason.CHARGING
            s.turnSignal != TurnSignal.OFF -> SuppressReason.TURN_SIGNAL
            s.cruiseOn -> SuppressReason.CRUISE
            abs(s.steeringDeg) > STEERING_LIMIT_DEG -> SuppressReason.STEERING
            else -> SuppressReason.NONE
        }

        val decelerating = s.gear == Gear.D && aSmooth < -DECEL_THRESHOLD && vSmoothMs > 0.5f
        val regenSignal = s.instantEvEfficiency >= REGEN_EFF_THRESHOLD

        val state = when {
            s.gear != Gear.D || vSmoothMs < 0.5f -> BrakeState.PARKED
            !decelerating -> BrakeState.DRIVING
            !s.brakePressed && regenSignal -> BrakeState.REGEN_ONLY
            s.brakePressed && regenSignal -> BrakeState.REGEN_AND_FRICTION
            s.brakePressed -> BrakeState.FRICTION_ONLY
            else -> BrakeState.REGEN_LIMITED
        }

        // 회생 비중: 회생이 감당할 수 있는 감속도 대비 실제 감속도
        val regenCap = regenDecelCap(s.socPercent, s.driveMode)
        val decel = abs(aSmooth)
        val regenShare = when (state) {
            BrakeState.REGEN_ONLY -> 1f
            BrakeState.REGEN_AND_FRICTION -> min(1f, regenCap / max(decel, 0.01f))
            else -> 0f
        }

        // 에너지 적산 (운동에너지 변화 × 회생 계수)
        if (decelerating) {
            val v2 = vSmoothMs
            val v1 = vPrev
            val dE = 0.5 * vehicleMassKg * (v1 * v1 - v2 * v2).coerceAtLeast(0f)
            regenRawJ += dE * regenShare
            regenJ += dE * regenShare * regenEfficiency
            if (state == BrakeState.FRICTION_ONLY || state == BrakeState.REGEN_AND_FRICTION) {
                frictionJ += dE * (1f - regenShare)
            }
        }

        val intensity = if (decelerating) min(1f, decel / 3.5f).coerceAtLeast(0.25f) else 0f
        val guide = updateGuide(s, decelerating)

        val coaching = CoachingState(
            brakeState = state,
            suppress = suppress,
            regenFill = intensity * regenShare,
            frictionFill = if (state == BrakeState.FRICTION_ONLY || state == BrakeState.REGEN_AND_FRICTION) intensity * (1f - regenShare) else 0f,
            guide = if (suppress == SuppressReason.NONE) guide else guide.copy(phase = GuidePhase.HIDDEN),
            speedKmh = vSmoothMs * 3.6f,
            night = s.lowBeamOn,
            ambientBrightness = s.ambientBrightness,
            gear = s.gear,
            socPercent = s.socPercent,
            tripRegenKwh = (regenJ / 3_600_000.0).toFloat(),
        )
        return coaching to report
    }

    private fun regenDecelCap(soc: Float, mode: DriveMode): Float {
        val socFactor = when {
            soc >= 95f -> 0.1f
            soc >= 90f -> 0.5f
            else -> 1f
        }
        val modeFactor = when (mode) {
            DriveMode.ECO -> 1.1f
            DriveMode.NORMAL -> 1f
            DriveMode.SPORT -> 0.9f
        }
        return 1.5f * socFactor * modeFactor
    }

    /** 기획서의 예측 감속 산출식 */
    private fun updateGuide(s: VehicleSnapshot, decelerating: Boolean): GuideState {
        val d = s.distanceToNextTbtM ?: return GuideState()
        val label = s.nextTbtLabel
        if (label != guideLabel) {
            guideLabel = label
            releaseShownAt = 0L
            scoredThisPoint = false
        }
        if (label != null && label.startsWith("직진")) return GuideState(label = label, distanceM = d)

        val v = vSmoothMs
        val vt = 0f
        val kMode = when (s.driveMode) {
            DriveMode.ECO -> 0.9f
            DriveMode.NORMAL -> 1f
            DriveMode.SPORT -> 1.15f
        }
        val slope = G * (s.gradePercent / 100f)              // 오르막 양수 → 감속을 도움
        val aEff = max(COMFORT_DECEL * kMode + slope, MIN_DECEL)
        val dDecel = (v * v - vt * vt) / (2f * aEff)
        val dTrigger = dDecel + v * REACTION_S
        val dShow = dTrigger + v * PREVIEW_S

        val aReq = if (d > 0.5f) (v * v - vt * vt) / (2f * d) else Float.MAX_VALUE
        val regenOnly = aReq <= regenDecelCap(s.socPercent, s.driveMode)

        // 안내 적중도 점수 (감속 시작 시 1회)
        if (decelerating && !scoredThisPoint && d <= dShow && v > 3f) {
            scoredThisPoint = true
            val gap = d - dTrigger   // 양수 = 일찍, 음수 = 늦게
            val score = max(0f, 1f - abs(gap) / max(dTrigger, 1f)) * 100f
            timingScores += score
            timingGaps += gap
        }

        val phase = when {
            v < 3f -> GuidePhase.HIDDEN
            decelerating && d <= dShow -> GuidePhase.DECELERATING
            d > dShow -> GuidePhase.HIDDEN
            d > dTrigger -> GuidePhase.APPROACH
            else -> {
                if (releaseShownAt == 0L) releaseShownAt = s.timestampMs
                if (s.timestampMs - releaseShownAt < 1500L) GuidePhase.RELEASE_NOW else GuidePhase.LATE
            }
        }
        val progress = if (dShow > dTrigger) ((dShow - d) / (dShow - dTrigger)).coerceIn(0f, 1f) else 0f
        return GuideState(phase, progress, d, dTrigger, label, regenOnly)
    }

    private fun resetTrip(startKm: Float) {
        regenJ = 0.0; regenRawJ = 0.0; frictionJ = 0.0
        tripStartKm = startKm
        timingScores.clear(); timingGaps.clear()
        guideLabel = null
    }

    private fun buildReport(): TripReport {
        val kwh = (regenJ / 3_600_000.0).toFloat()
        val dist = lastTripKm - tripStartKm
        val ratio = if (regenRawJ + frictionJ > 0) (regenRawJ / (regenRawJ + frictionJ) * 100).roundToInt() else 0
        val score = if (timingScores.isNotEmpty()) timingScores.average().roundToInt() else ratio
        val blocked = when {
            dist < 0.2f -> "주행 거리가 짧아 적립하지 않음"
            else -> null
        }
        val points = if (blocked == null) (kwh * POINTS_PER_KWH).roundToInt().coerceAtLeast(if (kwh > 0f) 1 else 0) else 0
        cumulativePoints += points
        return TripReport(
            distanceKm = dist,
            regenKwh = kwh,
            savedWon = (kwh * WON_PER_KWH).roundToInt(),
            co2Kg = kwh * CO2_PER_KWH,
            regenRatioPercent = ratio,
            decelScore = score.coerceIn(0, 100),
            avgTimingGapM = if (timingGaps.isNotEmpty()) timingGaps.average().roundToInt() else null,
            points = points,
            pointsBlockedReason = blocked,
        )
    }
}
