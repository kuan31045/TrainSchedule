package com.kappstudio.trainschedule.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kappstudio.trainschedule.domain.model.Station
import com.kappstudio.trainschedule.domain.repository.TrainRepository
import com.kappstudio.trainschedule.util.LoadApiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.kappstudio.trainschedule.data.Result
import com.kappstudio.trainschedule.domain.model.Name
import com.kappstudio.trainschedule.domain.model.Path
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val stationsOfCounty: Map<Name, List<Station>> = emptyMap(),
    val selectedStationType: SelectedStationType = SelectedStationType.DEPARTURE,
    val selectedCounty: Name = Name()
)

enum class SelectedStationType {
    DEPARTURE,
    ARRIVAL
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trainRepository: TrainRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val pathState: StateFlow<Path> = trainRepository.currentPath.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Path(),
        )

    var loadingState: LoadApiStatus by mutableStateOf(LoadApiStatus.Loading)
        private set

    init {
        getStations()
    }

    fun selectCounty(county: Name) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedCounty = county
            )
        }
    }

    fun selectStation(stationName: Name) {
        val county = uiState.value.selectedCounty
        val station =
            uiState.value.stationsOfCounty[county]?.first { station -> station.name == stationName }

        val path = when (uiState.value.selectedStationType) {
            SelectedStationType.DEPARTURE -> {
                Path(
                    departureStation = station ?: pathState.value.departureStation,
                    arrivalStation = pathState.value.arrivalStation
                )
            }

            SelectedStationType.ARRIVAL -> {
                Path(
                    departureStation = pathState.value.departureStation,
                    arrivalStation = station ?: pathState.value.arrivalStation
                )
            }
        }
        savePath(path)
    }

    private fun savePath(path: Path) {
        viewModelScope.launch {
            trainRepository.savePath(path)
            selectCounty(getCurrentPathCounty())
        }
    }

    fun changeSelectedStation(selectedStationType: SelectedStationType) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedStationType = selectedStationType
            )
        }

        selectCounty(getCurrentPathCounty())
    }

    fun swapPath() {
        savePath(
            Path(
                departureStation = pathState.value.arrivalStation,
                arrivalStation = pathState.value.departureStation
            )
        )
    }

    private fun getCurrentPathCounty(): Name {
        return when (uiState.value.selectedStationType) {
            SelectedStationType.DEPARTURE -> pathState.value.departureStation.county
            SelectedStationType.ARRIVAL -> pathState.value.arrivalStation.county
        }
    }

    private fun getStations() {
        viewModelScope.launch {
            val result = trainRepository.fetchStations()
            loadingState = when (result) {
                is Result.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            stationsOfCounty = result.data.groupBy { it.county }.minus(Name())
                        )
                    }
                    LoadApiStatus.Done
                }

                is Result.Fail -> {
                    LoadApiStatus.Error(result.error)
                }

                is Result.Error -> {
                    LoadApiStatus.Error(result.exception.toString())
                }

                else -> {
                    LoadApiStatus.Loading
                }
            }
        }
    }
}
