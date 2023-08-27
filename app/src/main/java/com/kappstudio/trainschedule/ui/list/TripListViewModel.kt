package com.kappstudio.trainschedule.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kappstudio.trainschedule.data.Result
import com.kappstudio.trainschedule.domain.model.Path
import com.kappstudio.trainschedule.domain.model.Trip
import com.kappstudio.trainschedule.domain.repository.TrainRepository
import com.kappstudio.trainschedule.ui.home.SelectedType
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.CAN_TRANSFER_BOOLEAN
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.TIME_TYPE_INT
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.TRAIN_TYPE_INT
import com.kappstudio.trainschedule.util.LoadingStatus
import com.kappstudio.trainschedule.util.TrainType
import com.kappstudio.trainschedule.util.getNowDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class TripListUiState(
    val trips: List<Trip> = emptyList(),
    val isFavorite: Boolean = false,
    val initialTripIndex: Int = 0,
    val filteredTrainTypes: List<TrainType> = emptyList(),
    val isFiltering: Boolean = false,
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    private val trainRepository: TrainRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val timeType: SelectedType =
        enumValues<SelectedType>()[savedStateHandle[TIME_TYPE_INT]!!]
    private val canTransfer: Boolean = savedStateHandle[CAN_TRANSFER_BOOLEAN]!!

    private val trips: MutableStateFlow<List<Trip>> = MutableStateFlow(emptyList())

    private val _uiState = MutableStateFlow(
        TripListUiState(
            filteredTrainTypes = TrainType.getTypes(savedStateHandle[TRAIN_TYPE_INT]!!)
        )
    )
    val uiState: StateFlow<TripListUiState> = _uiState.asStateFlow()

    var loadingState: LoadingStatus by mutableStateOf(LoadingStatus.Loading)
        private set

    val currentPath: StateFlow<Path> = trainRepository.currentPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Path(),
    )

    val dateTimeState: StateFlow<LocalDateTime> = trainRepository.selectedDateTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = getNowDateTime(),
    )

    init {
        checkFavorite()
        searchTrips()
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isFavorite = trainRepository.isCurrentPathFavorite()
                )
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            if (uiState.value.isFavorite) {
                trainRepository.deletePath(currentPath.value)
            } else {
                trainRepository.insertPath(currentPath.value)
            }
            checkFavorite()
        }
    }

    private fun setSpecifiedTimeTrip() {


        _uiState.update { currentState ->
            currentState.copy(
                initialTripIndex =
                if (timeType == SelectedType.DEPARTURE) {
                    currentState.trips.indexOfLast { it.startTime < dateTimeState.value } + 1
                } else {
                    currentState.trips.indexOfLast { it.endTime < dateTimeState.value }.let {
                        if (it > 0) it else 0
                    }
                }
            )
        }
    }

    private fun filterTrips() {
        val types = uiState.value.filteredTrainTypes.map { type -> type.typeCode }
        val newTrips: List<Trip> = trips.value.filter { trip ->
            trip.trainSchedules.all { schedule -> schedule.train.typeCode in types }
        }.sortedBy { it.startTime }
        _uiState.update { currentState ->
            currentState.copy(trips = newTrips)
        }
        setSpecifiedTimeTrip()
    }

    fun openFilter() {
        _uiState.update { currentState ->
            currentState.copy(isFiltering = !uiState.value.isFiltering)
        }
    }

    fun closeFilter(types: List<TrainType>) {
        _uiState.update { currentState ->
            currentState.copy(
                isFiltering = !uiState.value.isFiltering,
                filteredTrainTypes = types
            )
        }
        filterTrips()
    }

    fun searchTrips() {
        viewModelScope.launch {
            val result =
                if (canTransfer) {
                    trainRepository.fetchTransferTrips()
                } else {
                    trainRepository.fetchTrips()
                }

            loadingState = when (result) {
                is Result.Success -> {
                    trips.update { result.data.sortedBy { it.endTime } }
                    _uiState.update { currentState ->
                        currentState.copy(
                            trips = trips.value
                        )
                    }
                    filterTrips()
                    LoadingStatus.Done
                }

                is Result.Fail -> {
                    LoadingStatus.Error(result.error)
                }

                is Result.Error -> {
                    LoadingStatus.Error(result.exception.toString())
                }

                else -> {
                    LoadingStatus.Loading
                }
            }
        }
    }
}