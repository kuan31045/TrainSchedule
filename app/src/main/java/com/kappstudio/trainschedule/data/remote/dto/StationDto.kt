package com.kappstudio.trainschedule.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.kappstudio.trainschedule.domain.model.Name

data class StationDto(
    @SerializedName("StationAddress") val stationAddress: String?,
    @SerializedName("StationID") val stationId: String,
    @SerializedName("StationName") val stationName: Name,
)