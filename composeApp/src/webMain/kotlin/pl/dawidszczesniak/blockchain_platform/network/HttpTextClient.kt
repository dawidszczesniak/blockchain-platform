@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.Promise

interface HttpTextClient {
    suspend fun get(url: String): String
    suspend fun postJson(url: String, body: String): String
}

open class HttpTextClientException(message: String) : IllegalStateException(message)

class HttpStatusException(
    val method: String,
    val url: String,
    val statusCode: Int,
    val details: String,
) : HttpTextClientException("$method '$url' failed with HTTP $statusCode: $details")

class HttpNetworkException(
    val method: String,
    val url: String,
    val details: String,
) : HttpTextClientException("$method '$url' failed: $details")

class BrowserHttpTextClient(
    private val sessionExpirationNotifier: SessionExpirationNotifier? = null,
) : HttpTextClient {
    override suspend fun get(url: String): String {
        return suspendCancellableCoroutine { continuation ->
            fetchText(url).then(
                onFulfilled = { body ->
                    if (continuation.isActive) {
                        continuation.resume(jsAnyToString(body))
                    }
                    body
                },
                onRejected = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(toRequestException("GET", url, error))
                    }
                    error
                }
            )
        }
    }

    override suspend fun postJson(url: String, body: String): String {
        return suspendCancellableCoroutine { continuation ->
            val abortController = createAbortController()
            continuation.invokeOnCancellation {
                abortRequest(abortController)
            }
            postJsonText(url = url, body = body, abortController = abortController).then(
                onFulfilled = { responseBody ->
                    if (continuation.isActive) {
                        continuation.resume(jsAnyToString(responseBody))
                    }
                    responseBody
                },
                onRejected = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(toRequestException("POST", url, error))
                    }
                    error
                }
            )
        }
    }

    private fun toRequestException(method: String, url: String, error: JsAny?): HttpTextClientException {
        val exception = httpRequestException(method, url, error)
        if (exception is HttpStatusException) {
            val reason = exception.sessionExpirationReason()
            if (reason != null) {
                sessionExpirationNotifier?.notifySessionExpired(reason)
            }
        }
        return exception
    }
}

private fun httpRequestException(method: String, url: String, error: JsAny?): HttpTextClientException {
    val details = jsAnyToString(error)
    val statusCode = httpStatusPattern.find(details)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return if (statusCode != null) {
        HttpStatusException(
            method = method,
            url = url,
            statusCode = statusCode,
            details = details,
        )
    } else {
        HttpNetworkException(
            method = method,
            url = url,
            details = details,
        )
    }
}

private val httpStatusPattern = Regex("""HTTP\s+(\d{3})""")

@JsFun("(url) => fetch(url, { method: 'GET', cache: 'no-store', credentials: 'include' }).then(async r => { const text = await r.text(); if (!r.ok) { throw new Error('HTTP ' + r.status + (text ? ': ' + text : '')); } return text; })")
private external fun fetchText(url: String): Promise<JsAny?>

@JsFun(
    "(url, body, abortController) => fetch(url, { method: 'POST', cache: 'no-store', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body, signal: abortController.signal }).then(async r => { const text = await r.text(); if (!r.ok) { throw new Error('HTTP ' + r.status + (text ? ': ' + text : '')); } return text; })"
)
private external fun postJsonText(url: String, body: String, abortController: JsAny): Promise<JsAny?>

@JsFun("() => new AbortController()")
private external fun createAbortController(): JsAny

@JsFun("(abortController) => abortController.abort()")
private external fun abortRequest(abortController: JsAny)

@JsFun("(value) => String(value)")
private external fun jsAnyToString(value: JsAny?): String
