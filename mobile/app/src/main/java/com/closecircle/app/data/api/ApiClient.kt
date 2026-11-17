package com.closecircle.app.data.api

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [status] is 0 when the server could not be reached; [code] is the backend's stable Problem Details code. */
class ApiException(val status: Int, val code: String?, message: String? = null) : Exception(message ?: code ?: "HTTP $status") {
    val unauthorized: Boolean get() = status == 401
    val unreachable: Boolean get() = status == 0
}

class ApiClient(
    baseUrl: String,
    private val token: () -> String?,
    private val http: OkHttpClient = OkHttpClient(),
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val base = baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun <T> send(method: String, path: String, body: String? = null, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val requestBody = when {
            body != null -> body.toRequestBody(jsonType)
            method == "POST" || method == "PUT" -> "".toRequestBody(jsonType)
            else -> null
        }
        val request = Request.Builder()
            .url("$base$path")
            .method(method, requestBody)
            .header("Accept", "application/json")
            .apply { token()?.let { header("Authorization", "Bearer $it") } }
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) parse(text) else throw problem(response.code, text)
            }
        } catch (e: IOException) {
            throw ApiException(0, "NETWORK", e.message)
        }
    }

    /** Authentication-filter 401s may have no body, so a missing or unreadable Problem is not an error. */
    private fun problem(status: Int, text: String): ApiException {
        val p = runCatching { json.decodeFromString<ProblemDto>(text) }.getOrNull()
        return ApiException(status, p?.code, p?.detail ?: p?.title)
    }
}
