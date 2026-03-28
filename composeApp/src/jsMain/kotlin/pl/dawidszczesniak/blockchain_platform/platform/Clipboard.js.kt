@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.platform

import kotlin.JsFun
import kotlin.js.Promise

actual fun copyTextToClipboard(value: String) {
    writeClipboardText(value)
}

@JsFun("(value) => navigator.clipboard.writeText(value)")
private external fun writeClipboardText(value: String): Promise<*>
