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

class BrowserHttpTextClient : HttpTextClient {
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
                        val details = jsAnyToString(error)
                        continuation.resumeWithException(
                            IllegalStateException("Failed to fetch '$url': $details")
                        )
                    }
                    error
                }
            )
        }
    }

    override suspend fun postJson(url: String, body: String): String {
        return suspendCancellableCoroutine { continuation ->
            postJsonText(url = url, body = body).then(
                onFulfilled = { responseBody ->
                    if (continuation.isActive) {
                        continuation.resume(jsAnyToString(responseBody))
                    }
                    responseBody
                },
                onRejected = { error ->
                    if (continuation.isActive) {
                        val details = jsAnyToString(error)
                        continuation.resumeWithException(
                            IllegalStateException("Failed to POST '$url': $details")
                        )
                    }
                    error
                }
            )
        }
    }
}

@JsFun("(url) => fetch(url, { method: 'GET', cache: 'no-store' }).then(r => { if (!r.ok) { throw new Error('HTTP ' + r.status); } return r.text(); })")
private external fun fetchText(url: String): Promise<JsAny?>

@JsFun(
    "(url, body) => fetch(url, { method: 'POST', cache: 'no-store', headers: { 'Content-Type': 'application/json' }, body }).then(async r => { const text = await r.text(); if (!r.ok) { throw new Error(text || ('HTTP ' + r.status)); } return text; })"
)
private external fun postJsonText(url: String, body: String): Promise<JsAny?>

@JsFun("(value) => String(value)")
private external fun jsAnyToString(value: JsAny?): String
