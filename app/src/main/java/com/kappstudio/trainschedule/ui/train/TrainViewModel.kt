package com.kappstudio.trainschedule.ui.train

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kappstudio.trainschedule.R
import com.kappstudio.trainschedule.data.Result
import com.kappstudio.trainschedule.domain.model.StationLiveBoard
import com.kappstudio.trainschedule.domain.model.Train
import com.kappstudio.trainschedule.domain.model.TrainSchedule
import com.kappstudio.trainschedule.domain.repository.TrainRepository
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs
import com.kappstudio.trainschedule.util.LoadingStatus
import com.kappstudio.trainschedule.util.TrainType
import com.kappstudio.trainschedule.util.getNowDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class RunningStatus {
    NOT_YET,
    RUNNING,
    FINISH
}

data class TrainUiState(
    val trainSchedule: TrainSchedule = TrainSchedule(
        train = Train(
            number = "",
            type = TrainType.UNKNOWN
        ), stops = emptyList()
    ),
    val trainShortName: String = "",
    val delay: Long = 0,
    val runningStatus: RunningStatus = RunningStatus.NOT_YET,
    val liveBoards: List<StationLiveBoard> = emptyList(),
    val trainIndex: Int = 0,
    val currentTime: LocalDateTime = getNowDateTime(),
)

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val trainRepository: TrainRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TrainUiState(
            trainShortName = savedStateHandle[NavigationArgs.TRAIN_STRING]!!,
        )
    )
    val uiState: StateFlow<TrainUiState> = _uiState.asStateFlow()

    val dateTimeState: StateFlow<LocalDateTime> = trainRepository.selectedDateTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = getNowDateTime(),
    )

    var loadingState: LoadingStatus by mutableStateOf(LoadingStatus.Loading)
        private set

    init {
        fetchTrain()
    }

    private fun checkRunningStatus() {

        val nowTime = getNowDateTime()
        val notYet =
            nowTime < uiState.value.trainSchedule.stops.first().departureTime.minusHours(
                1
            )

        val isFinished =
            nowTime > uiState.value.trainSchedule.stops.last().arrivalTime.plusMinutes(
                uiState.value.delay
            )

        _uiState.update { currentState ->
            currentState.copy(
                runningStatus = when {
                    notYet -> RunningStatus.NOT_YET
                    isFinished -> RunningStatus.FINISH
                    else -> RunningStatus.RUNNING
                },
                trainIndex = if (isFinished) {
                    currentState.trainSchedule.stops.size - 1
                } else {
                    currentState.trainIndex
                }
            )
        }
    }

    fun fetchTrain() {
        loadingState = LoadingStatus.Loading
        val trainNumber = uiState.value.trainShortName.split("-").last()
        viewModelScope.launch {
            val result = trainRepository.fetchTrainSchedule(
                trainNumber = trainNumber,
            )

            loadingState = when (result) {
                is Result.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            trainSchedule = result.data
                        )
                    }
                    fetchStationLiveBoard()
                    LoadingStatus.Done
                }

                is Result.Fail -> {
                    LoadingStatus.Error(result.stringRes)
                }

                is Result.Error -> {
                    LoadingStatus.Error(R.string.api_maintenance)
                }

                else -> {
                    LoadingStatus.Loading
                }
            }
        }
    }

    fun fetchStationLiveBoard() {
        viewModelScope.launch {
            checkRunningStatus()
            while (uiState.value.runningStatus == RunningStatus.RUNNING) {

                val liveBoardResult = trainRepository.fetchStationLiveBoardOfTrain(
                    trainNumber = uiState.value.trainSchedule.train.number,
                )
                if (liveBoardResult.isNotEmpty()) {
                    val index = uiState.value.trainSchedule.stops.indexOfFirst {
                        it.station.id == liveBoardResult.first().stationId
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            delay = liveBoardResult.first().delay,
                            liveBoards = liveBoardResult,
                            trainIndex = index,
                            currentTime = getNowDateTime()
                        )
                    }
                }

                checkRunningStatus()
                delay((20000L..30000L).random())
            }
        }
    }
}