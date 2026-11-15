package com.team_o.regencoach.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** 시연용 페달 조작 */
enum class PedalInput { ACCEL, HOLD, LIFT, BRAKE }

/**
 * Pleos Connect 에뮬레이터 없이도 앱 흐름을 확인할 수 있는 가상 주행 데이터 공급원.
 * 실제 차량의 SDK 응답과 같은 형태(VehicleSnapshot)를 10Hz 로 내보낸다.
 */
class SimulatedVehicleDataSource : VehicleDataSource {

    override val name: String = "시뮬레이션"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var scenario: Job? = null

    private val _snapshots = MutableStateFlow<VehicleUi<VehicleSnapshot>>(VehicleUi.Loading)
    override val snapshots: StateFlow<VehicleUi<VehicleSnapshot>> = _snapshots.asStateFlow()

    private val _apiLog = MutableStateFlow<List<String>>(emptyList())
    override val apiLog: StateFlow<List<String>> = _apiLog.asStateFlow()

    // ---- 조작 상태 ----
    private val _pedal = MutableStateFlow(PedalInput.HOLD)
    val pedal: StateFlow<PedalInput> = _pedal.asStateFlow()
    private val _gear = MutableStateFlow(Gear.P)
    val gear: StateFlow<Gear> = _gear.asStateFlow()
    private val _turn = MutableStateFlow(TurnSignal.OFF)
    val turn: StateFlow<TurnSignal> = _turn.asStateFlow()
    private val _cruise = MutableStateFlow(false)
    val cruise: StateFlow<Boolean> = _cruise.asStateFlow()
    private val _night = MutableStateFlow(false)
    val night: StateFlow<Boolean> = _night.asStateFlow()
    private val _batteryFull = MutableStateFlow(false)
    val batteryFull: StateFlow<Boolean> = _batteryFull.asStateFlow()
    private val _downhill = MutableStateFlow(false)
    val downhill: StateFlow<Boolean> = _downhill.asStateFlow()
    private val _scenarioRunning = MutableStateFlow(false)
    val scenarioRunning: StateFlow<Boolean> = _scenarioRunning.asStateFlow()

    // ---- 물리 상태 ----
    private var speedMs = 0f
    private var tripKm = 0f
    private var soc = 72f
    private var distToTbt = 600f
    private var tbtIndex = 0
    private val tbtLabels = listOf("우회전 · 판교역 방면", "좌회전 · 분당구청", "직진 · 정자역", "우회전 · 회사")

    fun setPedal(p: PedalInput) {
        _pedal.value = p
        if (p != PedalInput.HOLD) _cruise.value = false
        log(if (p == PedalInput.BRAKE) "registerBrakePedalStatus → true" else "registerBrakePedalStatus → false")
    }

    fun setGear(g: Gear) {
        _gear.value = g
        if (g == Gear.P) { speedMs = 0f; _pedal.value = PedalInput.HOLD; _cruise.value = false }
        log("CarPropertyManager GEAR_SELECTION → $g")
    }

    fun toggleTurn() {
        _turn.value = if (_turn.value == TurnSignal.OFF) TurnSignal.RIGHT else TurnSignal.OFF
        log("getCurrentTurnSignalState → ${_turn.value}")
    }

    fun toggleCruise() {
        _cruise.value = !_cruise.value
        if (_cruise.value) _pedal.value = PedalInput.HOLD
        log("getCruiseControlState → ${_cruise.value}")
    }

    fun toggleNight() {
        _night.value = !_night.value
        log("getCurrentLowBeamLightState → ${_night.value}")
    }

    fun toggleBatteryFull() {
        _batteryFull.value = !_batteryFull.value
        soc = if (_batteryFull.value) 99f else 72f
        log("getEvBatteryStateOfCharge → ${soc.toInt()}%")
    }

    fun toggleDownhill() {
        _downhill.value = !_downhill.value
        log("FusedLocation 고도 변화 → 경사 ${if (_downhill.value) -3 else 0}%")
    }

    /** 출퇴근 한 구간을 자동으로 재현하는 시연 시나리오 */
    fun playScenario() {
        scenario?.cancel()
        scenario = scope.launch {
            _scenarioRunning.value = true
            setGear(Gear.D)
            val steps = listOf(
                PedalInput.ACCEL to 7000L,
                PedalInput.HOLD to 4000L,
                PedalInput.LIFT to 5000L,
                PedalInput.ACCEL to 5000L,
                PedalInput.HOLD to 3000L,
                PedalInput.BRAKE to 2500L,
                PedalInput.ACCEL to 6000L,
                PedalInput.LIFT to 9000L,
            )
            for ((p, ms) in steps) {
                setPedal(p)
                delay(ms)
            }
            setPedal(PedalInput.HOLD)
            setGear(Gear.P)
            _scenarioRunning.value = false
        }
    }

    override fun start() {
        if (loop != null) return
        log("Vehicle SDK initialize() → onComplete")
        log("checkCapabilities(CAR_DRIVE, CAR_ENERGY, CAR_LIGHTS) → READ")
        loop = scope.launch {
            val dt = 0.1f
            while (isActive) {
                step(dt)
                delay(100)
            }
        }
    }

    override fun stop() {
        loop?.cancel(); loop = null
        scenario?.cancel(); scenario = null
    }

    private fun step(dt: Float) {
        val driving = _gear.value == Gear.D
        val p = if (_cruise.value) PedalInput.HOLD else _pedal.value
        val grade = if (_downhill.value) -3f else 0f
        val gravity = 9.81f * grade / 100f

        // 가감속 (m/s²)
        val accel = when {
            !driving -> 0f
            p == PedalInput.ACCEL -> if (speedMs < 22.2f) 1.6f else 0f
            p == PedalInput.HOLD -> 0f
            p == PedalInput.LIFT -> -1.2f + gravity
            else -> -3.2f + gravity
        }
        val before = speedMs
        speedMs = if (driving) max(0f, min(27.8f, speedMs + accel * dt)) else 0f
        val ds = (before + speedMs) / 2f * dt
        tripKm += ds / 1000f

        val decelerating = speedMs < before - 0.001f
        val regenPossible = speedMs > 2.2f && soc < 95f

        // 순간 전비 (km/kWh): 계기판처럼 회생 중에는 상한값(99.9)으로 표시된다고 가정
        val instantEff = when {
            !driving || speedMs < 0.5f -> 0f
            decelerating && regenPossible -> 99.9f
            decelerating -> 25f
            p == PedalInput.ACCEL -> 4.2f
            else -> 6.8f
        }

        // 배터리: 회생 중 소폭 충전, 가속 중 소폭 방전 (시연용 근사)
        if (!_batteryFull.value) {
            soc = when {
                decelerating && regenPossible -> min(94.9f, soc + 0.004f)
                p == PedalInput.ACCEL && driving -> max(5f, soc - 0.003f)
                else -> soc
            }
        }

        // 내비 TBT 지점
        if (driving) {
            distToTbt -= ds
            if (distToTbt <= 0f) {
                distToTbt = 600f
                tbtIndex = (tbtIndex + 1) % tbtLabels.size
            }
        }

        _snapshots.value = VehicleUi.Ready(
            VehicleSnapshot(
                timestampMs = System.currentTimeMillis(),
                speedKmh = speedMs * 3.6f,
                gear = _gear.value,
                brakePressed = driving && p == PedalInput.BRAKE,
                tripInstantDistanceKm = ds / 1000f,
                tripDistanceKm = tripKm,
                driveMode = DriveMode.NORMAL,
                cruiseOn = _cruise.value,
                steeringDeg = if (_turn.value == TurnSignal.OFF) 0f else 35f,
                instantEvEfficiency = instantEff,
                socPercent = soc,
                charging = false,
                lowBeamOn = _night.value,
                ambientBrightness = if (_night.value) 3 else 8,
                turnSignal = _turn.value,
                distanceToNextTbtM = if (driving) distToTbt else null,
                nextTbtLabel = if (driving) tbtLabels[tbtIndex] else null,
                gradePercent = grade,
            )
        )
    }

    private fun log(line: String) {
        val list = _apiLog.value
        _apiLog.value = (listOf(line) + list).take(8)
    }
}
