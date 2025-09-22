package page.pinniped.ION_details.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainComplicationService : SuspendingComplicationDataSourceService() {
    companion object {
        private const val TAG = "MainCompService"
        private const val ROUTE_ID_301 = "301"
        private const val STOP_ID_SOUTHBOUND_TARGET = "6004"
        private const val STOP_ID_NORTHBOUND_TARGET = "6120"
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        // Consistent preview format
        return createComplicationData("00:00›00:00", "ION details")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val feedMessage = fetchAndParseTripUpdates()

        val currentTime = LocalTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("mm:ss", Locale.getDefault())

        var southboundGradeCrossingTime = Long.MAX_VALUE
        var northboundGradeCrossingTime = Long.MAX_VALUE

        val southboundGradeCrossingTimeOffset = 50
        val northboundGradeCrossingTimeOffset = 10

        if (feedMessage != null) {
            var southboundNextArrivalTime = Long.MAX_VALUE
            var northboundNextArrivalTime = Long.MAX_VALUE

            val currentTimeSeconds = System.currentTimeMillis() / 1000

            for (entity in feedMessage.entityList) {
                if (entity.hasTripUpdate() && entity.tripUpdate.trip.routeId == ROUTE_ID_301) {
                    val tripUpdate = entity.tripUpdate
                    for (stopTimeUpdate in tripUpdate.stopTimeUpdateList) {
                        if (stopTimeUpdate.hasArrival() && stopTimeUpdate.arrival.hasTime()) {
                            val arrivalTime = stopTimeUpdate.arrival.time

                            if (stopTimeUpdate.stopId == STOP_ID_SOUTHBOUND_TARGET) {
                                if (arrivalTime < southboundNextArrivalTime && arrivalTime > currentTimeSeconds) {
                                    southboundNextArrivalTime = arrivalTime
                                    if (arrivalTime - southboundGradeCrossingTimeOffset > currentTimeSeconds) southboundGradeCrossingTime = arrivalTime - southboundGradeCrossingTimeOffset
                                }
                            }
                            else if (stopTimeUpdate.stopId == STOP_ID_NORTHBOUND_TARGET) {
                                if (arrivalTime < northboundNextArrivalTime && arrivalTime > currentTimeSeconds) {
                                    northboundNextArrivalTime = arrivalTime
                                    if (arrivalTime - northboundGradeCrossingTimeOffset > currentTimeSeconds) northboundGradeCrossingTime = arrivalTime - northboundGradeCrossingTimeOffset
                                }
                            }
                        }
                    }
                }
            }
        } else {
            return createComplicationData("${currentTime.format(timeFormatter)}›ERROR", "ION details")
        }

        val gateTime = LocalTime.ofSecondOfDay(Math.min(southboundGradeCrossingTime, northboundGradeCrossingTime) % 86400)
        return createComplicationData("${currentTime.format(timeFormatter)}›${gateTime.format(timeFormatter)}", "ION details")
    }

    private suspend fun fetchAndParseTripUpdates(): GtfsRealtime.FeedMessage? {
        return withContext(Dispatchers.IO) {
            val url = URL("https://webapps.regionofwaterloo.ca/api/grt-routes/api/tripupdates")
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream
                    GtfsRealtime.FeedMessage.parseFrom(inputStream)
                } else {
                    logErrorStream(connection)
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                inputStream?.close()
                connection?.disconnect()
            }
        }
    }

    private fun logErrorStream(connection: HttpURLConnection?) {
        try {
            connection?.errorStream?.let { errorStream ->
                val reader = BufferedReader(InputStreamReader(errorStream))
                val errorResponse = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    errorResponse.append(line)
                }
                reader.close()
            }
        } catch (_: Exception) {
        }
    }


    private fun createComplicationData(text: String, contentDescription: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = text).build(),
            contentDescription = PlainComplicationText.Builder(text = contentDescription).build()
        ).build()

}
