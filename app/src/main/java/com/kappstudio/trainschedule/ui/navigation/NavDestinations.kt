package com.kappstudio.trainschedule.ui.navigation

import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.CAN_TRANSFER_BOOLEAN
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.TIME_TYPE_INT
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.TRAIN_STRING
import com.kappstudio.trainschedule.ui.navigation.NavigationArgs.TRAIN_TYPE_INT

enum class Screen(val route: String) {
    PARENT(route = "parent"),
    SPLASH(route = "splash"),
    HOME(route = "home"),
    STATION(route = "station"),
    TRIPS(route = "trips"),
    FAVORITE(route = "favorite"),
    DETAIL(route = "detail"),
    TRAIN(route = "train")
}

object NavigationArgs {
    const val TIME_TYPE_INT = "timeType"
    const val TRAIN_TYPE_INT = "trainType"
    const val CAN_TRANSFER_BOOLEAN = "canTransfer"
    const val TRAIN_STRING = "train"
}

object RoutesWithArgs {
    val TRIPS =
        "${Screen.TRIPS.route}/{$TIME_TYPE_INT}/{$TRAIN_TYPE_INT}/{$CAN_TRANSFER_BOOLEAN}"
    val TRAIN =
        "${Screen.TRAIN.route}/{$TRAIN_STRING}"
}