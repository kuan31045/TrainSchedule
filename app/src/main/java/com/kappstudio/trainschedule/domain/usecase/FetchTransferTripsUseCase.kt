package com.kappstudio.trainschedule.domain.usecase

import com.kappstudio.trainschedule.domain.model.Path
import com.kappstudio.trainschedule.domain.model.Station
import com.kappstudio.trainschedule.domain.model.Train
import com.kappstudio.trainschedule.domain.model.TrainSchedule
import com.kappstudio.trainschedule.domain.model.Trip
import com.kappstudio.trainschedule.domain.repository.TrainRepository
import com.kappstudio.trainschedule.util.TrainType
import com.kappstudio.trainschedule.util.addDate
import com.kappstudio.trainschedule.util.dateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import timber.log.Timber
import java.net.URLEncoder
import java.time.LocalDate
import javax.inject.Inject
import com.kappstudio.trainschedule.data.Result

const val BASIC_URL =
    "https://tip.railway.gov.tw/tra-tip-web/tip/tip001/tip112/querybytime?startTime=00:00&endTime=23:59&transfer=NORMAL&trainTypeList=ALL&rideDate="
/**
 * Scrape timetable document and parse it into trip list.
 */
class FetchTransferTripsUseCase @Inject constructor(
    private val trainRepository: TrainRepository,
) {
    suspend operator fun invoke(): Result<List<Trip>> = withContext(Dispatchers.IO) {
        val path = trainRepository.currentPath.first()
        val date = trainRepository.selectedDateTime.first().toLocalDate()
        val stations = trainRepository.getAllStations()

        val result =
            trainRepository.scrapeTimetablesDocFromTwRailwayWeb(getSearchUrl(date, path))

        when (result) {
            is Result.Success -> {
                try {
                    var trips = parseTripV1(
                        allStations = stations,
                        date = date,
                        currentPath = path,
                        doc = result.data
                    )
                    if (trips.isEmpty()) {
                        trips = parseTripV2(
                            allStations = stations,
                            date = date,
                            currentPath = path,
                            doc = result.data
                        )
                    }
                    Result.Success(trips)

                } catch (e: Exception) {
                    Result.Error(e)
                }
            }

            is Result.Loading -> Result.Loading

            is Result.Fail -> result

            is Result.Error -> result
        }
    }
}

private fun getSearchUrl(
    date: LocalDate,
    path: Path,
): String {
    val rideDate: String = date.format(dateFormatter)
    val startStation: String =
        path.departureStation.id + "-" + URLEncoder.encode(path.departureStation.name.zh, "UTF-8")
    val endStation: String =
        path.arrivalStation.id + "-" + URLEncoder.encode(path.arrivalStation.name.zh, "UTF-8")

    return "$BASIC_URL$rideDate&endStation=$endStation&startStation=$startStation&sort=travelTime,asc"
}

private fun parseTripV1(
    allStations: List<Station>,
    date: LocalDate,
    currentPath: Path,
    doc: Document,
): List<Trip> {
    val elements = doc.select(".detail-box-td")

    // 去除發車時間相同
    val tripElements = elements.distinctBy {
        it.select(".detail-column").first()?.select(".time")
            ?.first()
            ?.text()
    }

    val trips = tripElements.mapNotNull { element ->
        try {
            val time = mutableListOf<String>()
            val schedules = element.select(".detail-column").map { schedule ->
                val train = schedule.select(".train-type a.links").text()
                val startTime = schedule.select(".time").first()?.text()
                val endTime = schedule.select(".time").last()?.text()
                val stations = schedule.select(".location")
                val path = "${stations[2].text()} ${stations[3].text()}"
                time.add("$startTime $endTime")
                TrainSchedule(
                    path = Path(
                        departureStation = allStations.first {
                            it.name.zh == path.split(" ").first()
                        },
                        arrivalStation = allStations.first {
                            it.name.zh == path.split(" ").last()
                        }
                    ),
                    train = Train(
                        number = train.split(")").last().filter { it.isDigit() },
                        type = TrainType.fromZh(train.filter { !it.isDigit() }.trim())
                    )
                )
            }

            val startTime: String = time.first().split(" ").first()
            val endTime = time.last().split(" ").last()
            val nextDay: Long = if (startTime < endTime) 0 else 1

            Trip(
                path = currentPath,
                startTime = startTime.addDate(date),
                endTime = endTime.addDate(date).plusDays(nextDay),
                trainSchedules = schedules
            )
        } catch (e: Exception) {
            Timber.w("Parser Trip error: $e")
            null
        }
    }
    return trips.sortedBy { it.startTime }
}

private fun parseTripV2(
    allStations: List<Station>,
    date: LocalDate,
    currentPath: Path,
    doc: Document,
): List<Trip> {
    val elements = doc.select(".bk_3_list.columns")

    // 去除發車時間相同
    val tripElements = elements.distinctBy {
        it.select(".ts_3_trans1 .bk_3bg2").first()?.text()
    }

    val trips = tripElements.mapNotNull { element ->
        try {
            val trains = element.select(".ts_1_trans1 .icon-train a.links").map { it.text() }
            val departureTime = element.select(".ts_3_trans1 .bk_3bg2").first()?.text()!!
            val arrivalTime = element.select(".ts_3_trans1 ~ .ts_3_trans1 .bk_3bg2").text()
            val paths = element.select(".m100_x35 .bk_3bg2").map {
                (it.select(".pl-1").last()?.text() ?: "").replace(Regex("[0-9:]"), "")
            }

            val nextDay: Long = if (departureTime < arrivalTime) 0 else 1

            Trip(
                path = currentPath,
                startTime = departureTime.addDate(date),
                endTime = arrivalTime.addDate(date).plusDays(nextDay),
                trainSchedules = trains.mapIndexed { index, train ->
                    TrainSchedule(
                        path = Path(
                            departureStation =
                            allStations.first { it.name.zh == paths[index].split(" ").first() },
                            arrivalStation = allStations.first {
                                it.name.zh == paths[index].split(" ").last()
                            }
                        ),
                        train = Train(
                            number = train.split(")").last().filter { it.isDigit() },
                            type = TrainType.fromZh(train.filter { !it.isDigit() }.trim())
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Timber.w("Parser Trip error: $e")
            null
        }
    }
    return trips.sortedBy { it.startTime }
}