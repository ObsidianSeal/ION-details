package page.pinniped.ION_details.complication

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.transit.realtime.GtfsRealtime // Import the generated class
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

/**
 * Skeleton for complication data source that returns short text.
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {
    companion object {
        private const val TAG = "MainCompService"
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("00:00›00:00", "ION data test")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        Log.d(TAG, "onComplicationRequest called at: ${System.currentTimeMillis()}")
        Log.d(TAG, "Requested type: ${request.complicationType}, Instance ID: ${request.complicationInstanceId}")

        var displayableText = "N/A" // Default text in case of error

        // Fetch and parse the GTFS-realtime data
        val feedMessage = fetchAndParseTripUpdates()

        if (feedMessage != null) {
            // Now you have the parsed FeedMessage. You need to extract the data you want.
            // This is highly dependent on what information you want to display.
            // For example, let's try to find the first trip update and its arrival time.
            if (feedMessage.entityCount > 0) {
                val firstEntity = feedMessage.getEntity(0)
                if (firstEntity.hasTripUpdate()) {
                    val tripUpdate = firstEntity.tripUpdate
                    if (tripUpdate.stopTimeUpdateCount > 0) {
                        val firstStopUpdate = tripUpdate.getStopTimeUpdate(0)
                        if (firstStopUpdate.hasArrival()) {
                            val arrivalTime = firstStopUpdate.arrival.time // This is typically a Unix timestamp (seconds)
                            // You'll need to format this timestamp into a human-readable string
                            // For simplicity, let's just use the timestamp as a placeholder
                            displayableText = "Arrival: ${LocalTime.MIN.plusSeconds(arrivalTime).format(DateTimeFormatter.ofPattern("HH:mm"))}" // Example formatting
                            Log.d(TAG, "First arrival time: $arrivalTime")
                        } else {
                            Log.d(TAG, "First stop update has no arrival time.")
                        }
                    } else {
                        Log.d(TAG, "First trip update has no stop time updates.")
                    }
                } else {
                    Log.d(TAG, "First entity is not a trip update.")
                }
            } else {
                Log.d(TAG, "FeedMessage is empty.")
            }
        } else {
            Log.e(TAG, "Failed to fetch or parse trip updates.")
        }

        // Fallback to dynamic time if GTFS data processing fails or gives no specific text
        if (displayableText == "N/A") {
            displayableText = getComplicationText()
        }
        Log.d(TAG, "Generated dynamic text: $displayableText")


        return ShortTextComplicationData.Builder(
//            text = PlainComplicationText.Builder(text = displayableText).build(),
            text = PlainComplicationText.Builder(text = "00:00›00:00").build(),
            contentDescription = PlainComplicationText.Builder(text = "ION dynamic data").build()
        ).build()
    }

    private suspend fun fetchAndParseTripUpdates(): GtfsRealtime.FeedMessage? {
        return withContext(Dispatchers.IO) {
            val url = URL("https://webapps.regionofwaterloo.ca/api/grt-routes/api/tripupdates")
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                // Consider adding headers like Accept for protobuf if the server needs it
                // connection.setRequestProperty("Accept", "application/x-protobuf")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream
                    // Parse the binary data directly from the InputStream
                    GtfsRealtime.FeedMessage.parseFrom(inputStream)
                } else {
                    Log.e(TAG, "Error fetching trip updates: $responseCode")
                    // Log error response body if helpful
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        val reader = BufferedReader(InputStreamReader(errorStream))
                        val errorResponse = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            errorResponse.append(line)
                        }
                        reader.close()
                        Log.e(TAG, "Error response body: $errorResponse")
                    }
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during fetch or parse", e)
                null
            } finally {
                inputStream?.close()
                connection?.disconnect()
            }
        }
    }


    // This function can now be a fallback or used if GTFS data is unavailable
    private fun getComplicationText(): String {
        val currentTime = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("mm:ss", Locale.getDefault())
        val formattedTime = currentTime.format(formatter)
        return "${formattedTime}›00:00" // Your original placeholder
    }

    private fun createComplicationData(text: String, contentDescription: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).build()

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        Log.d(TAG, "Complication $complicationInstanceId activated with type $type")
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        super.onComplicationDeactivated(complicationInstanceId)
        Log.d(TAG, "Complication $complicationInstanceId deactivated")
    }

    // fetchTripUpdates() is replaced by fetchAndParseTripUpdates()
    // The old fetchTripUpdates() that returns String is no longer needed if parsing directly
}
