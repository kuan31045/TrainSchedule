package com.kappstudio.trainschedule.domain.model

data class TrainSchedule(
    val path: Path = Path(),
    val train: Train,
    val price: Int = 0,
    val stops: List<Stop> = emptyList(),
) {
    fun getStartTime() = stops.first().departureTime
    fun getEndTime() = stops.last().arrivalTime
}