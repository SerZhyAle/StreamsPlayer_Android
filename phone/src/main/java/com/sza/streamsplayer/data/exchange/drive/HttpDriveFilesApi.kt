package com.sza.streamsplayer.data.exchange.drive

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [DriveFilesApi] over Drive's REST API with plain OkHttp: no Google client library, one scope
 * ([DriveAppFolder.SCOPE]), every call inside `appDataFolder`. A 401 is answered once with a fresh token.
 * Nothing here logs a token, a file id, a file name or a file body.
 */
class HttpDriveFilesApi(
    private val client: OkHttpClient,
    private val tokens: DriveTokenSource,
    private val baseUrl: String = DRIVE_BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) : DriveFilesApi {

    override suspend fun list(namePrefix: String): DriveListing {
        val found = mutableListOf<DriveFileInfo>()
        var driveNowMs = clock()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = send { token ->
                val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
                    .addQueryParameter("spaces", DriveAppFolder.SPACE)
                    .addQueryParameter("pageSize", PAGE_SIZE.toString())
                    .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                    .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                    .build()
                authorized(token).url(url).get().build()
            }
            response.use {
                driveNowMs = it.headers.getDate("Date")?.time ?: driveNowMs
                val root = parseObject(it.peekBody(MAX_LISTING_BYTES).string())
                (root.get("files") as? JsonArray)?.forEach { element ->
                    val file = element as? JsonObject ?: return@forEach
                    val id = file.text("id")
                    val name = file.text("name")
                    val modified = file.text("modifiedTime")?.let(::parseRfc3339)
                    if (id != null && name != null && modified != null && name.startsWith(namePrefix)) {
                        found += DriveFileInfo(id, name, modified)
                    }
                }
                pageToken = root.text("nextPageToken")
            }
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        Timber.d("S0029: drive listing ${found.size} file(s)")
        return DriveListing(found, driveNowMs)
    }

    override suspend fun read(fileId: String): String? {
        val response = try {
            send { token ->
                val url = "$baseUrl/drive/v3/files/$fileId".toHttpUrl().newBuilder()
                    .addQueryParameter("alt", "media")
                    .build()
                authorized(token).url(url).get().build()
            }
        } catch (_: DriveException.NotFound) {
            return null
        }
        return response.use { it.peekBody(MAX_RECORD_BYTES).string() }
    }

    override suspend fun create(name: String, content: String): String {
        val metadata = JsonObject().apply {
            addProperty("name", name)
            addProperty("mimeType", JSON_TYPE)
            add("parents", JsonArray().apply { add(DriveAppFolder.SPACE) })
        }
        val response = send { token ->
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toString().toRequestBody(JSON_MEDIA))
                .addPart(content.toRequestBody(JSON_MEDIA))
                .build()
            val url = "$baseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", "id")
                .build()
            authorized(token).url(url).post(body).build()
        }
        return response.use { answer ->
            parseObject(answer.peekBody(MAX_RECORD_BYTES).string()).text("id") ?: throw DriveException.Failure(answer.code)
        }
    }

    override suspend fun update(fileId: String, content: String) {
        send { token ->
            val url = "$baseUrl/upload/drive/v3/files/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "media")
                .addQueryParameter("fields", "id")
                .build()
            authorized(token).url(url).patch(content.toRequestBody(JSON_MEDIA)).build()
        }.close()
    }

    override suspend fun delete(fileId: String) {
        try {
            send { token ->
                authorized(token).url("$baseUrl/drive/v3/files/$fileId").delete().build()
            }.close()
        } catch (_: DriveException.NotFound) {
            // Already gone is the state a delete wants.
        }
    }

    private fun authorized(token: String): Request.Builder =
        Request.Builder().header("Authorization", "Bearer $token")

    /** Sends the request [build] makes; a 401 is retried once with a fresh token. Returns a successful response. */
    private suspend fun send(build: (token: String) -> Request): Response {
        var token = tokens.token()
        var refreshed = false
        while (true) {
            val response = client.newCall(build(token)).await()
            when {
                response.isSuccessful -> return response
                response.code == HTTP_UNAUTHORIZED && !refreshed -> {
                    response.close()
                    tokens.invalidate(token)
                    token = tokens.token()
                    refreshed = true
                }
                else -> response.use { throw failureOf(it) }
            }
        }
    }

    private fun failureOf(response: Response): DriveException {
        val code = response.code
        val reason = runCatching {
            val error = parseObject(response.peekBody(MAX_ERROR_BYTES).string()).get("error") as? JsonObject
            ((error?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject)?.text("reason")
        }.getOrNull()
        Timber.d("S0029: drive answered $code${reason?.let { " reason=$it" }.orEmpty()}")
        return when {
            code == HTTP_UNAUTHORIZED -> DriveException.Unauthorized()
            code == HTTP_NOT_FOUND -> DriveException.NotFound()
            code == HTTP_TOO_MANY -> DriveException.RateLimited()
            code == HTTP_FORBIDDEN -> when (reason) {
                "rateLimitExceeded", "userRateLimitExceeded", "sharingRateLimitExceeded" -> DriveException.RateLimited()
                "storageQuotaExceeded" -> DriveException.DriveFull()
                "dailyLimitExceeded", "uploadLimitExceeded" -> DriveException.DailyLimit()
                else -> DriveException.Forbidden(reason ?: "unspecified")
            }
            code >= HTTP_SERVER_ERROR -> DriveException.Server(code)
            else -> DriveException.Failure(code)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(DriveException.Network(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }
        })
    }

    private fun parseObject(text: String): JsonObject =
        runCatching { JsonParser.parseString(text) as? JsonObject }.getOrNull() ?: JsonObject()

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    companion object {
        const val DRIVE_BASE_URL = "https://www.googleapis.com"
        private const val JSON_TYPE = "application/json"
        private val JSON_MEDIA = "$JSON_TYPE; charset=UTF-8".toMediaType()
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
        private const val MAX_LISTING_BYTES = 512L * 1024
        private const val MAX_RECORD_BYTES = 32L * 1024
        private const val MAX_ERROR_BYTES = 8L * 1024
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_ERROR = 500

        private val RFC3339_PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")

        /** Drive's `modifiedTime`: RFC 3339 in UTC. Null for anything else. */
        fun parseRfc3339(value: String): Long? = RFC3339_PATTERNS.firstNotNullOfOrNull { pattern ->
            try {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(value)?.time
            } catch (_: ParseException) {
                null
            }
        }
    }
}
