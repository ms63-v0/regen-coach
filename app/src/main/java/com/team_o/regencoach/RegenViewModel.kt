package com.team_o.regencoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.team_o.regencoach.data.SimulatedVehicleDataSource
import com.team_o.regencoach.data.VehicleUi
import com.team_o.regencoach.domain.CoachingState
import com.team_o.regencoach.domain.RegenEngine
import com.team_o.regencoach.domain.TripReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegenViewModel : ViewModel() {

    /** 실차 연동 시 PleosVehicleDataSource 로 교체 */
    val sim = SimulatedVehicleDataSource()
    private val engine = RegenEngine()

    private val _coaching = MutableStateFlow(CoachingState())
    val coaching: StateFlow<CoachingState> = _coaching.asStateFlow()

    private val _sdkState = MutableStateFlow<VehicleUi<Unit>>(VehicleUi.Loading)
    val sdkState: StateFlow<VehicleUi<Unit>> = _sdkState.asStateFlow()

    private val _report = MutableStateFlow<TripReport?>(null)
    val report: StateFlow<TripReport?> = _report.asStateFlow()

    private val _points = MutableStateFlow(0)
    val points: StateFlow<Int> = _points.asStateFlow()

    private val _coachingOn = MutableStateFlow(true)
    val coachingOn: StateFlow<Boolean> = _coachingOn.asStateFlow()

    init {
        sim.start()
        viewModelScope.launch {
            sim.snapshots.collect { ui ->
                when (ui) {
                    is VehicleUi.Ready -> {
                        _sdkState.value = VehicleUi.Ready(Unit)
                        val (state, trip) = engine.update(ui.value)
                        _coaching.value = state
                        if (trip != null) {
                            _report.value = trip
                            _points.value = engine.cumulativePoints
                        }
                    }
                    VehicleUi.Loading -> _sdkState.value = VehicleUi.Loading
                    VehicleUi.Unsupported -> _sdkState.value = VehicleUi.Unsupported
                    VehicleUi.PermissionRequired -> _sdkState.value = VehicleUi.PermissionRequired
                    is VehicleUi.Error -> _sdkState.value = VehicleUi.Error(ui.code)
                }
            }
        }
    }

    fun toggleCoaching() { _coachingOn.value = !_coachingOn.value }
    fun dismissReport() { _report.value = null }

    override fun onCleared() {
        sim.stop()
        super.onCleared()
    }
}
