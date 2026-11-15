package com.team_o.regencoach.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos Connect Vehicle SDK 연동부.
 *
 * Vehicle SDK AAR 은 Pleos Playground 에서만 배포되어 이 저장소에는 포함하지 않았다.
 * AAR 을 app/libs 에 넣고 build.gradle.kts 의 fileTree 의존성을 켠 뒤,
 * 아래 TODO 위치에 실제 호출을 연결하면 [SimulatedVehicleDataSource] 와 같은
 * [VehicleSnapshot] 을 내보내게 된다. 화면과 계산 로직은 바꿀 필요가 없다.
 *
 * 사용 API (권한)
 *  - CAR_DRIVE  : registerBrakePedalStatus, getTripInstantaneousDrivingDistance,
 *                 getTripCurrentDrivingDistance/Time, getCurrentDrivingModeState,
 *                 getCruiseControlState, getCurrentSteeringWheelAngle
 *  - CAR_ENERGY : getInstantaneousEvEfficiency, getEvBatteryStateOfCharge,
 *                 getCurrentEvEfficiency, getCumulativeEvEfficiency, getEvChargeSwitchState,
 *                 getInstantaneousFuelEconomy (HEV)
 *  - CAR_LIGHTS : getCurrentLowBeamLightState, getAmbientLightBrightness, getCurrentTurnSignalState
 *  - NaviHelper : 경로 정보 조회 (다음 TBT 지점까지 남은 거리)
 *  - FusedLocation : registerFusedLocationCallback (고도 → 경사)
 *  - 속도·기어 : AAOS CarPropertyManager
 *
 * SDK 호출은 onComplete / onFailed 비동기 콜백이므로 결과를 [VehicleUi] 다섯 상태로 변환한다.
 *  - initialize() 직후            → Loading
 *  - onComplete                   → Ready
 *  - capability accessType NONE   → Unsupported
 *  - 권한 미선언·거부              → PermissionRequired
 *  - 그 밖의 onFailed             → Error(errorCode)
 */
class PleosVehicleDataSource(@Suppress("unused") private val context: Context) : VehicleDataSource {

    override val name: String = "Pleos Vehicle SDK"

    private val _snapshots = MutableStateFlow<VehicleUi<VehicleSnapshot>>(VehicleUi.Loading)
    override val snapshots: StateFlow<VehicleUi<VehicleSnapshot>> = _snapshots.asStateFlow()

    private val _apiLog = MutableStateFlow<List<String>>(emptyList())
    override val apiLog: StateFlow<List<String>> = _apiLog.asStateFlow()

    override fun start() {
        // TODO(Pleos SDK): Vehicle.initialize(context) → onComplete 에서 capability 확인 후 구독 시작
        // TODO(Pleos SDK): registerBrakePedalStatus { pressed -> latest = latest.copy(brakePressed = pressed) }
        // TODO(Pleos SDK): 100ms 주기로 getInstantaneousEvEfficiency / getEvBatteryStateOfCharge 조회
        _apiLog.value = listOf("Vehicle SDK AAR 미포함 → Unsupported")
        _snapshots.value = VehicleUi.Unsupported
    }

    override fun stop() {
        // TODO(Pleos SDK): 등록한 콜백 해제
    }
}
