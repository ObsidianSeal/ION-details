package page.pinniped.ION_details.complication

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
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

        val dynamicText = getComplicationText()
        Log.d(TAG, "Generated dynamic text: $dynamicText")

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = dynamicText).build(),
            contentDescription = PlainComplicationText.Builder(text = "ION dynamic data").build()
        ).build()
    }

    private fun getComplicationText(): String {
        val currentTime = LocalTime.now()

        val formatter = DateTimeFormatter.ofPattern("mm:ss", Locale.getDefault())
        val formattedTime = currentTime.format(formatter)

        return "${formattedTime}›00:00"
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
}