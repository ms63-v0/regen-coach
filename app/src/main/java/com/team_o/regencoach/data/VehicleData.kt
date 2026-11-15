package com.team_o.regencoach.data

import kotlinx.coroutines.flow.Flow

/** 기어 상태 (속도·기어는 AAOS CarPropertyManager 경로) */
enum class Gear { P, R, N, D }

/** 방향지시등 상태 (CAR_LIGHTS · getCurrentTurnSignalState) */
enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

/** 드라이브 모드 (CAR_DRIVE · getCurrentDrivingModeState) */
enum class DriveMode { ECO, NORMAL, SPORT }

/**
 * 한 시점의 차량 신호 묶음.
 * 각 필드 옆 주석은 실제 차량에서 값을 읽어 오는 Pleos SDK API.
 */
data class VehicleSnapshot(
    val timestampMs: Long,
    /** km/h · AAOS CarPropertyManager (PERF_VEHICLE_SPEED) */
    val speedKmh: Float,
    /** AAOS CarPropertyManager (GEAR_SELECTION) */
    val gear: Gear,
    /** CAR_DRIVE · getCurrentBrakePedalStatus (true = 밟음) */
    val brakePressed: Boolean,
    /** CAR_DRIVE · getTripInstantaneousDrivingDistance (km) */
    val tripInstantDistanceKm: Float,
    /** CAR_DRIVE · getTripCurrentDrivingDistance (km) */
    val tripDistanceKm: Float,
    /** CAR_DRIVE · getCurrentDrivingModeState */
    val driveMode: DriveMode,
    /** CAR_DRIVE · getCruiseControlState */
    val cruiseOn: Boolean,
    /** CAR_DRIVE · getCurrentSteeringWheelAngle (deg) */
    val steeringDeg: Float,
    /** CAR_ENERGY · getInstantaneousEvEfficiency (km/kWh) */
    val instantEvEfficiency: Float,
    /** CAR_ENERGY · getEvBatteryStateOfCharge (%) */
    val socPercent: Float,
    /** CAR_ENERGY · getEvChargeSwitchState */
    val charging: Boolean,
    /** CAR_LIGHTS · getCurrentLowBeamLightState */
    val lowBeamOn: Boolean,
    /** CAR_LIGHTS · getAmbientLightBrightness (0~10) */
    val ambientBrightness: Int,
    /** CAR_LIGHTS · getCurrentTurnSignalState */
    val turnSignal: TurnSignal,
    /** NaviHelper · 경로 정보 조회 (다음 TBT 지점까지 남은 거리, m). 경로 없음 = null */
    val distanceToNextTbtM: Float?,
    /** NaviHelper · 다음 TBT 안내 문구 */
    val nextTbtLabel: String?,
    /** FusedLocation · 고도 변화로 계산한 노면 경사 (%), 오르막 양수 */
    val gradePercent: Float,
)

/** Pleos 디자인 시스템이 요구하는 다섯 가지 SDK 상태 */
sealed interface VehicleUi<out T> {
    data object Loading : VehicleUi<Nothing>
    data class Ready<T>(val value: T) : VehicleUi<T>
    data object Unsupported : VehicleUi<Nothing>
    data object PermissionRequired : VehicleUi<Nothing>
    data class Error(val code: Int?) : VehicleUi<Nothing>
}

/** 차량 데이터 공급원. 실차는 [PleosVehicleDataSource], 시연은 [SimulatedVehicleDataSource]. */
interface VehicleDataSource {
    val name: String
    val snapshots: Flow<VehicleUi<VehicleSnapshot>>
    /** 호출된 SDK API 이름 기록 (시연 화면의 호출 로그용) */
    val apiLog: Flow<List<String>>
    fun start()
    fun stop()
}
