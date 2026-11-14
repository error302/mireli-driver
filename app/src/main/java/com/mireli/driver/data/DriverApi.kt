package com.mireli.driver.data

import com.mireli.driver.BuildConfig
import com.mireli.driver.domain.SgrTimetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalTime
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class DriverApi {
    private companion object {
        const val StatusTimeoutMillis = 8_000L
        val statusClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(StatusTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }
    suspend fun request(path:String,method:String="GET",token:String?=null,json:JSONObject?=null,bytes:ByteArray?=null,headers:Map<String,String> = emptyMap()):JSONObject {
        require(path.startsWith("/") && !path.contains(".."))
        return requestJson("/api/v1/driver$path", method, token, json, bytes, headers)
    }

    internal suspend fun serviceStatus(): JSONObject {
        // Android's synchronous resolver can block before socket timeouts start.
        // The async request lets startup recover even while the resolver is still waiting.
        return withTimeoutOrNull(StatusTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(serviceUri("/api/v1/driver/status").toString())
                    .header("Accept", "application/json").build()
                val call = statusClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        continuation.resumeWithException(error)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val status = runCatching {
                            response.use {
                                val stream = it.body?.byteStream()
                                parseResponse(it.code, stream?.let(::readBoundedBody) ?: "{}", usedSession = false)
                            }
                        }
                        continuation.resumeWith(status)
                    }
                })
            }
        } ?: throw DriverServiceException(503, "Connection timed out. Check your network and try again.")
    }

    internal suspend fun timetable(): List<SgrTimetable.Service> {
        val trains = requestJson("/api/trains").getJSONArray("trains")
        return (0 until trains.length()).mapNotNull { index ->
            val train = trains.getJSONObject(index)
            val outbound = train.getString("originCode") == "MTM" && train.getString("destCode") == "NTM"
            val inbound = train.getString("originCode") == "NTM" && train.getString("destCode") == "MTM"
            if (!outbound && !inbound) return@mapNotNull null
            val days = train.getJSONArray("serviceDays")
            val clock = LocalTime.parse(train.getString(if (outbound) "originTime" else "destTime"))
            val offset = if (outbound) 0 else train.getInt("destDayOffset")
            require(offset in 0..1)
            val serviceDays = (0 until days.length()).map { days.getInt(it) }.toSet()
            require(serviceDays.isNotEmpty() && serviceDays.all { it in 0..6 })
            SgrTimetable.Service(train.getString("name"),
                if (outbound) SgrTimetable.Movement.Departure else SgrTimetable.Movement.Arrival,
                clock, offset.toLong(), serviceDays)
        }
    }

    private suspend fun requestJson(
        path: String,
        method: String = "GET",
        token: String? = null,
        json: JSONObject? = null,
        bytes: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.contains(".."))
        val connection=serviceUri(path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/json")
            token?.let {connection.setRequestProperty("Authorization","Bearer $it")}
            val payload=bytes?:json?.toString()?.toByteArray(Charsets.UTF_8)
            if(payload!=null) {
                connection.doOutput=true;connection.setFixedLengthStreamingMode(payload.size)
                connection.setRequestProperty("Content-Type",if(bytes!=null)headers["Content-Type"]?:"application/octet-stream" else "application/json")
            }
            headers.forEach {(key,value)->connection.setRequestProperty(key,value)}
            if(payload!=null)connection.outputStream.use {it.write(payload)}
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            parseResponse(status, stream?.use(::readBoundedBody) ?: "{}", usedSession = token != null)
        } finally {connection.disconnect()}
    }

    private fun serviceUri(path: String): URI {
        val base = URI(BuildConfig.DRIVER_SERVICE_URL)
        require(base.scheme == "https" ||
            (BuildConfig.DRIVER_SERVICE_TEST && base.scheme == "http" && base.host == "10.0.2.2"))
        return base.resolve(path)
    }

    private fun readBoundedBody(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(out.size() + count <= 512 * 1024) { "The service response is too large." }
            out.write(buffer, 0, count)
        }
        return out.toString("UTF-8")
    }

    private fun parseResponse(status: Int, body: String, usedSession: Boolean): JSONObject {
        val result = runCatching { JSONObject(body) }.getOrNull()
        if (status !in 200..299) {
            val code = result?.opt("code") as? String
            val message = (result?.opt("error") as? String)?.takeIf { it.isNotBlank() }
                ?: "The driver service is unavailable. Try again later."
            throw DriverServiceException(status, message, code, usedSession)
        }
        return result ?: throw DriverServiceException(503, "The service returned an invalid response.")
    }
}

class DriverServiceException(
    val status: Int,
    message: String,
    val code: String? = null,
    private val usedSession: Boolean = false
) : Exception(message) {
    val sessionRejected: Boolean
        get() = usedSession && status == 401 && code in setOf("UNAUTHENTICATED", "SESSION_EXPIRED")

    val tripActionRejected: Boolean
        get() = code != null && status in 400..499 && status !in setOf(401, 408, 425, 429)
}
