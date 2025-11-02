package com.clepsy.android.network

import com.clepsy.android.models.MobileAppUsageEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val APP_USAGE_ENDPOINT = "/sources/aggregator/mobile/app-usage"

interface ClepsyApi {
    suspend fun pair(request: PairingRequest): ApiResult<PairingResponse>
    suspend fun sendAppUsageEvent(deviceToken: String, event: MobileAppUsageEvent): ApiResult<Unit>
    suspend fun sendHeartbeat(deviceToken: String): ApiResult<Unit>
}

class HttpClepsyApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ClepsyApi {

    override suspend fun pair(request: PairingRequest): ApiResult<PairingResponse> = withContext(ioDispatcher) {
        val body = json.encodeToString(PairingRequest.serializer(), request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url("$baseUrl/sources/pair")
            .post(body)
            .build()

        executeJson(httpRequest, PairingResponse.serializer())
    }

    override suspend fun sendAppUsageEvent(
        deviceToken: String,
        event: MobileAppUsageEvent
    ): ApiResult<Unit> = withContext(ioDispatcher) {
        val payload = json.encodeToString(
            MobileAppUsagePayload.serializer(),
            MobileAppUsagePayload.from(event)
        ).toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(baseUrl + APP_USAGE_ENDPOINT)
            .post(payload)
            .header("Authorization", "Bearer $deviceToken")
            .build()

        executeEmpty(httpRequest)
    }

    override suspend fun sendHeartbeat(deviceToken: String): ApiResult<Unit> = withContext(ioDispatcher) {
        val httpRequest = Request.Builder()
            .url("$baseUrl/sources/source-heartbeats")
            .put("{}".toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $deviceToken")
            .build()

        executeEmpty(httpRequest)
    }

    private fun <T> executeJson(request: Request, deserializer: DeserializationStrategy<T>): ApiResult<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    return ApiResult.Failure(code, body)
                }
                val result = json.decodeFromString(deserializer, body)
                ApiResult.Success(result)
            }
        } catch (ioe: IOException) {
            ApiResult.Failure(null, ioe.message, ioe)
        }
    }

    private fun executeEmpty(request: Request): ApiResult<Unit> {
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = response.body?.string()
                    return ApiResult.Failure(response.code, message)
                }
            }
            ApiResult.Success(Unit)
        } catch (ioe: IOException) {
            ApiResult.Failure(null, ioe.message, ioe)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
data class PairingRequest(
    val code: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("source_type") val sourceType: String = "mobile"
)

@Serializable
data class PairingResponse(
    @SerialName("device_token") val deviceToken: String,
    @SerialName("source_id") val sourceId: Int
)

@Serializable
private data class MobileAppUsagePayload(
    @SerialName("event_type") val eventType: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_label") val appLabel: String,
    val timestamp: String,
    @SerialName("activity_name") val activityName: String? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, String>? = null,
    @SerialName("notification_text") val notificationText: String? = null
) {
    companion object {
        private const val EVENT_TYPE = "mobile_app_usage"

        fun from(event: MobileAppUsageEvent): MobileAppUsagePayload = MobileAppUsagePayload(
            eventType = EVENT_TYPE,
            packageName = event.packageName,
            appLabel = event.appLabel,
            timestamp = event.timestamp.toPythonIsoString(),
            activityName = event.activityName,
            mediaMetadata = event.mediaMetadata?.takeIf { it.isNotEmpty() },
            notificationText = event.notificationText?.takeIf { it.isNotBlank() }
        )
    }
}

private fun kotlinx.datetime.Instant.toPythonIsoString(): String {
    val iso = toString()
    return if (iso.endsWith("Z")) iso.removeSuffix("Z") + "+00:00" else iso
}
