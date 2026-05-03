@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.login

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.Promise

data class WalletNetworkTarget(
    val chainId: Long,
    val networkName: String,
)

data class WalletDescriptor(
    val id: String,
    val name: String,
    val rdns: String?,
    val iconUri: String?,
)

data class WalletLoginContext(
    val walletAddress: String,
    val chainId: Long,
)

data class WalletTransactionRequest(
    val to: String,
    val data: String,
    val valueHex: String = "0x0",
)

data class WalletTransactionReceipt(
    val transactionHash: String,
    val status: String? = null,
)

data class WalletSessionCandidate(
    val walletId: String,
    val walletAddress: String,
)

enum class WalletRuntimeEventType {
    AccountsChanged,
    ChainChanged,
    Disconnect,
}

data class WalletRuntimeEvent(
    val type: WalletRuntimeEventType,
    val walletAddress: String? = null,
)

fun interface WalletSessionSubscription {
    fun cancel()
}

interface WalletProvider {
    suspend fun discoverWallets(): List<WalletDescriptor>
    suspend fun findConnectedWallet(expectedWalletAddress: String): WalletSessionCandidate?
    fun watchWalletSession(walletId: String, listener: (WalletRuntimeEvent) -> Unit): WalletSessionSubscription?
    suspend fun requestLoginContext(walletId: String, targetNetwork: WalletNetworkTarget? = null): WalletLoginContext
    suspend fun signMessage(walletId: String, walletAddress: String, message: String): String
    suspend fun sendTransaction(walletId: String, walletAddress: String, request: WalletTransactionRequest): String
    suspend fun waitForTransactionReceipt(walletId: String, txHash: String): WalletTransactionReceipt
}

class InjectedWalletProvider : WalletProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private var nextWalletWatcherId = 1L

    override suspend fun discoverWallets(): List<WalletDescriptor> {
        val payload = awaitJsAsString { discoverWalletsJson() }
        val walletsJson = runCatching {
            json.parseToJsonElement(payload).jsonArray
        }.getOrElse {
            throw IllegalStateException("Wallet discovery payload is invalid.")
        }
        return walletsJson.map { walletJson ->
            val wallet = walletJson.jsonObject
            val id = wallet.requiredString("id")
            val name = wallet.requiredString("name")
            WalletDescriptor(
                id = id,
                name = name,
                rdns = wallet["rdns"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null },
                iconUri = wallet["icon"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null },
            )
        }
    }

    override suspend fun findConnectedWallet(expectedWalletAddress: String): WalletSessionCandidate? {
        val normalizedExpectedAddress = expectedWalletAddress.trim().lowercase()
        if (normalizedExpectedAddress.isBlank()) {
            return null
        }
        return discoverWallets().firstNotNullOfOrNull { wallet ->
            val payload = awaitJsAsString { getConnectedWalletAccountsJson(wallet.id) }
            val accounts = runCatching {
                json.parseToJsonElement(payload).jsonArray.mapNotNull { accountJson ->
                    accountJson.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                }
            }.getOrElse {
                emptyList()
            }
            val matchingAddress = accounts.firstOrNull { account ->
                account.lowercase() == normalizedExpectedAddress
            }
            matchingAddress?.let { account ->
                WalletSessionCandidate(
                    walletId = wallet.id,
                    walletAddress = account,
                )
            }
        }
    }

    override fun watchWalletSession(
        walletId: String,
        listener: (WalletRuntimeEvent) -> Unit,
    ): WalletSessionSubscription? {
        val watcherId = "bp-wallet-watch-${nextWalletWatcherId++}"
        val registered = registerWalletSessionListener(
            walletId = walletId,
            watcherId = watcherId,
        ) { eventType, walletAddress ->
            val runtimeEventType = when (eventType.trim()) {
                "accountsChanged" -> WalletRuntimeEventType.AccountsChanged
                "chainChanged" -> WalletRuntimeEventType.ChainChanged
                "disconnect" -> WalletRuntimeEventType.Disconnect
                else -> null
            } ?: return@registerWalletSessionListener
            listener(
                WalletRuntimeEvent(
                    type = runtimeEventType,
                    walletAddress = walletAddress.trim().ifBlank { null },
                )
            )
        }
        if (!registered) {
            return null
        }
        return WalletSessionSubscription {
            unregisterWalletSessionListener(
                walletId = walletId,
                watcherId = watcherId,
            )
        }
    }

    override suspend fun requestLoginContext(walletId: String, targetNetwork: WalletNetworkTarget?): WalletLoginContext {
        val payload = awaitJsAsString {
            requestWalletLoginContextJson(
                walletId = walletId,
                targetChainId = targetNetwork?.chainId ?: 0L,
                targetNetworkName = targetNetwork?.networkName.orEmpty(),
            )
        }
        val walletContext = runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrElse {
            throw IllegalStateException("Wallet context payload is invalid.")
        }
        val chainId = walletContext["chainId"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalStateException("Wallet chainId is invalid.")
        return WalletLoginContext(
            walletAddress = walletContext.requiredString("walletAddress"),
            chainId = chainId,
        )
    }

    override suspend fun signMessage(walletId: String, walletAddress: String, message: String): String {
        return awaitJsAsString {
            signPersonalMessage(walletId, walletAddress, message)
        }
    }

    override suspend fun sendTransaction(
        walletId: String,
        walletAddress: String,
        request: WalletTransactionRequest,
    ): String {
        return awaitJsAsString {
            sendWalletTransaction(
                walletId = walletId,
                walletAddress = walletAddress,
                to = request.to,
                data = request.data,
                valueHex = request.valueHex,
            )
        }
    }

    override suspend fun waitForTransactionReceipt(walletId: String, txHash: String): WalletTransactionReceipt {
        val payload = awaitJsAsString {
            waitForWalletTransactionReceipt(walletId, txHash)
        }
        val receipt = runCatching {
            json.parseToJsonElement(payload).jsonObject
        }.getOrElse {
            throw IllegalStateException("Wallet receipt payload is invalid.")
        }
        return WalletTransactionReceipt(
            transactionHash = receipt.requiredString("transactionHash"),
            status = receipt["status"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun awaitJsAsString(call: () -> Promise<JsAny?>): String {
        return suspendCancellableCoroutine { continuation ->
            call().then(
                onFulfilled = { value ->
                    if (continuation.isActive) {
                        continuation.resume(jsAnyToString(value))
                    }
                    value
                },
                onRejected = { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(jsAnyToString(error))
                        )
                    }
                    error
                },
            )
        }
    }
}

@JsFun(
    """
async () => {
  const global = globalThis;
  const providerRegistry = global.__bpWalletProviders ?? (global.__bpWalletProviders = {});
  const discoveredWallets = [];
  const seenProviders = new Set();
  const seenIds = new Set();
  const walletByIdentity = new Map();
  const genericWalletNames = new Set(["", "wallet", "injected wallet"]);
  const officialWalletSvg = {
    metamask: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24" class="web3icons"><path fill="#FF5C16" d="m19.821 19.918-3.877-1.131-2.924 1.712h-2.04l-2.926-1.712-3.875 1.13L3 16.02l1.179-4.327L3 8.034 4.179 3.5l6.056 3.544h3.53L19.821 3.5 21 8.034l-1.179 3.658L21 16.02z"/><path fill="#FF5C16" d="m4.18 3.5 6.055 3.547-.24 2.434zm3.875 12.52 2.665 1.99-2.665.777zm2.452-3.286-.512-3.251-3.278 2.21h-.002v.001l.01 2.275 1.33-1.235zM19.82 3.5l-6.056 3.547.24 2.434zm-3.875 12.52-2.665 1.99 2.665.777zm1.339-4.326v-.002zl-3.279-2.21-.512 3.25h2.451l1.33 1.236z"/><path fill="#E34807" d="m8.054 18.787-3.875 1.13L3 16.022h5.054zm2.452-6.054.74 4.7-1.026-2.614-3.497-.85 1.33-1.236zm5.44 6.054 3.875 1.13L21 16.022h-5.055zm-2.452-6.054-.74 4.7 1.026-2.614 3.497-.85-1.331-1.236z"/><path fill="#FF8D5D" d="m3 16.02 1.179-4.328h2.535l.01 2.276 3.496.85 1.026 2.613-.527.576-2.665-1.989H3zm18 0-1.179-4.328h-2.535l-.01 2.276-3.496.85-1.026 2.613.527.576 2.665-1.989H21zm-7.235-8.976h-3.53l-.24 2.435 1.251 7.95h1.508l1.252-7.95z"/><path fill="#661800" d="M4.179 3.5 3 8.034l1.179 3.658h2.535l3.28-2.211zm5.594 10.177H8.625l-.626.6 2.222.54zM19.821 3.5 21 8.034l-1.179 3.658h-2.535l-3.28-2.211zm-5.593 10.177h1.15l.626.6-2.224.541zm-1.209 5.271.262-.94-.527-.575h-1.509l-.527.575.262.94"/><path fill="#C0C4CD" d="M13.02 18.948V20.5h-2.04v-1.552z"/><path fill="#E7EBF6" d="m8.055 18.785 2.927 1.714v-1.552l-.262-.94zm7.89 0L13.02 20.5v-1.552l.262-.94z"/></svg>`,
    coinbase: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24" class="web3icons"><path fill="#0E5BFF" d="M3 12a9 9 0 1 1 18 0 9 9 0 0 1-18 0"/><path fill="#fff" fill-rule="evenodd" d="M12 18.375a6.375 6.375 0 1 0 0-12.75 6.375 6.375 0 0 0 0 12.75m-.75-8.25c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125h1.5c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125z" clip-rule="evenodd"/></svg>`,
    rabby: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24" class="web3icons"><path fill="url(#rabby__a)" d="M20.908 13.17c.707-1.644-2.788-6.235-6.127-8.148-2.105-1.482-4.298-1.279-4.742-.628-.975 1.428 3.228 2.638 6.038 4.05a3.25 3.25 0 0 0-1.508 1.39c-1.048-1.19-3.347-2.216-6.046-1.39-1.819.556-3.33 1.868-3.914 3.85a1.1 1.1 0 0 0-.464-.103c-.632 0-1.145.533-1.145 1.191s.513 1.191 1.145 1.191c.117 0 .483-.081.483-.081l5.855.044c-2.341 3.865-4.192 4.43-4.192 5.1s1.77.488 2.436.238c3.182-1.195 6.6-4.919 7.187-5.99 2.463.32 4.533.357 4.994-.715"/><path fill="url(#rabby__b)" fill-rule="evenodd" d="M16.077 8.444c.13-.053.11-.253.074-.41-.082-.362-1.5-1.82-2.833-2.473-1.815-.89-3.152-.843-3.35-.433.37.788 2.085 1.529 3.875 2.302.764.33 1.541.666 2.234 1.014" clip-rule="evenodd"/><path fill="url(#rabby__c)" fill-rule="evenodd" d="M13.774 16.38c-.367-.145-.782-.28-1.254-.4.503-.937.609-2.322.134-3.199-.666-1.229-1.503-1.883-3.446-1.883-1.07 0-3.947.374-3.998 2.874q-.009.393.018.724l5.255.04a18.7 18.7 0 0 1-1.953 2.696c.698.186 1.273.342 1.802.486.501.136.96.26 1.44.388a22 22 0 0 0 2.002-1.726" clip-rule="evenodd"/><path fill="url(#rabby__d)" d="M4.539 14.24c.215 1.898 1.252 2.642 3.371 2.863 2.12.22 3.335.072 4.954.225 1.352.128 2.559.845 3.006.598.403-.223.178-1.029-.361-1.546-.7-.67-1.667-1.135-3.369-1.3.34-.967.244-2.322-.282-3.06-.762-1.065-2.168-1.547-3.948-1.337-1.859.22-3.64 1.173-3.371 3.556"/><defs><linearGradient id="rabby__a" x1="8.311" x2="20.827" y1="11.714" y2="15.125" gradientUnits="userSpaceOnUse"><stop stop-color="#8697FF"/><stop offset="1" stop-color="#ABB7FF"/></linearGradient><linearGradient id="rabby__b" x1="18.66" x2="9.323" y1="11.468" y2="2.473" gradientUnits="userSpaceOnUse"><stop stop-color="#8697FF"/><stop offset="1" stop-color="#5156D8" stop-opacity="0"/></linearGradient><linearGradient id="rabby__c" x1="14.023" x2="5.231" y1="16.707" y2="11.849" gradientUnits="userSpaceOnUse"><stop stop-color="#465EED"/><stop offset="1" stop-color="#8697FF" stop-opacity="0"/></linearGradient><linearGradient id="rabby__d" x1="9.054" x2="15.173" y1="11.617" y2="19.089" gradientUnits="userSpaceOnUse"><stop stop-color="#8898FF"/><stop offset=".984" stop-color="#6277F1"/></linearGradient></defs></svg>`,
  };
  const toSvgDataUrl = (svg) => {
    if (typeof svg !== "string") {
      return "";
    }
    const normalizedSvg = svg.trim();
    if (normalizedSvg.length === 0) {
      return "";
    }
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(normalizedSvg);
  };
  const officialWalletIconByFamily = {
    metamask: toSvgDataUrl(officialWalletSvg.metamask),
    coinbase: toSvgDataUrl(officialWalletSvg.coinbase),
    rabby: toSvgDataUrl(officialWalletSvg.rabby),
  };
  const readBlobAsDataUrl = (blob) => new Promise((resolve, reject) => {
    try {
      const reader = new FileReader();
      reader.onloadend = () => resolve(String(reader.result || ""));
      reader.onerror = () => reject(reader.error || new Error("Failed to read icon blob."));
      reader.readAsDataURL(blob);
    } catch (error) {
      reject(error);
    }
  });
  const loadImage = (src) => new Promise((resolve, reject) => {
    const image = new Image();
    if (typeof src === "string" && !src.startsWith("data:")) {
      image.crossOrigin = "anonymous";
    }
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Failed to decode wallet icon."));
    image.src = src;
  });
  const normalizeIconToPngDataUrl = async (iconUri) => {
    if (typeof iconUri !== "string") {
      return "";
    }
    const raw = iconUri.trim();
    if (raw.length === 0) {
      return "";
    }
    const sourceDataUrl = raw.startsWith("data:")
      ? raw
      : await (async () => {
          try {
            const response = await fetch(raw, { cache: "force-cache" });
            if (!response.ok) {
              return "";
            }
            const blob = await response.blob();
            return await readBlobAsDataUrl(blob);
          } catch (_) {
            return "";
          }
        })();
    if (sourceDataUrl.length === 0) {
      return "";
    }
    if (sourceDataUrl.startsWith("data:image/png;base64,")) {
      return sourceDataUrl;
    }
    try {
      const image = await loadImage(sourceDataUrl);
      const width = Math.max(1, Number(image.naturalWidth || image.width || 24));
      const height = Math.max(1, Number(image.naturalHeight || image.height || 24));
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d");
      if (!context) {
        return sourceDataUrl;
      }
      context.clearRect(0, 0, width, height);
      context.drawImage(image, 0, 0, width, height);
      return canvas.toDataURL("image/png");
    } catch (_) {
      return sourceDataUrl;
    }
  };
  const normalize = (value) => {
    return typeof value === "string" ? value.trim().toLowerCase() : "";
  };
  const defaultWalletName = (provider) => {
    if (provider?.isRabby) {
      return "Rabby Wallet";
    }
    if (provider?.isCoinbaseWallet) {
      return "Coinbase Wallet";
    }
    if (provider?.isMetaMask) {
      return "MetaMask";
    }
    return "Injected Wallet";
  };
  const defaultWalletRdns = (provider) => {
    if (provider?.isRabby) {
      return "io.rabby";
    }
    if (provider?.isCoinbaseWallet) {
      return "com.coinbase.wallet";
    }
    if (provider?.isMetaMask) {
      return "io.metamask";
    }
    return "";
  };
  const isGenericName = (name) => {
    return genericWalletNames.has(normalize(name));
  };
  const detectFamily = (provider, rdns, walletName) => {
    const rdnsKey = normalize(rdns);
    const nameKey = normalize(walletName);
    if (rdnsKey.includes("rabby") || nameKey.includes("rabby") || provider?.isRabby) {
      return "rabby";
    }
    if (rdnsKey.includes("coinbase") || nameKey.includes("coinbase") || provider?.isCoinbaseWallet) {
      return "coinbase";
    }
    if (rdnsKey.includes("metamask") || nameKey.includes("metamask")) {
      return "metamask";
    }
    if (provider?.isMetaMask && !provider?.isRabby && !provider?.isCoinbaseWallet) {
      return "metamask";
    }
    return "";
  };
  const buildIdentityKey = (family, rdns, walletName) => {
    if (family.length > 0) {
      return "family:" + family;
    }
    const rdnsKey = normalize(rdns);
    if (rdnsKey.length > 0) {
      return "rdns:" + rdnsKey;
    }
    const nameKey = normalize(walletName);
    if (nameKey.length > 0 && !isGenericName(walletName)) {
      return "name:" + nameKey;
    }
    return "";
  };
  const shouldPreferName = (currentName, candidateName) => {
    const currentKey = normalize(currentName);
    const candidateKey = normalize(candidateName);
    if (candidateKey.length === 0) {
      return false;
    }
    if (currentKey.length === 0) {
      return true;
    }
    return isGenericName(currentName) && !isGenericName(candidateName);
  };

  const registerProvider = (provider, info) => {
    if (!provider || typeof provider.request !== "function") {
      return;
    }
    if (seenProviders.has(provider)) {
      return;
    }
    seenProviders.add(provider);

    const walletName = typeof info?.name === "string" && info.name.trim().length > 0
      ? info.name.trim()
      : defaultWalletName(provider);
    const rdns = typeof info?.rdns === "string" && info.rdns.trim().length > 0
      ? info.rdns.trim()
      : defaultWalletRdns(provider);
    const family = detectFamily(provider, rdns, walletName);
    const fallbackIcon = family.length > 0 ? (officialWalletIconByFamily[family] || "") : "";
    const icon = typeof info?.icon === "string" && info.icon.trim().length > 0
      ? info.icon.trim()
      : fallbackIcon;
    const identityKey = buildIdentityKey(family, rdns, walletName);
    if (identityKey.length > 0) {
      const existingWallet = walletByIdentity.get(identityKey);
      if (existingWallet) {
        if ((!existingWallet.icon || existingWallet.icon.length === 0) && icon.length > 0) {
          existingWallet.icon = icon;
        }
        if ((!existingWallet.rdns || existingWallet.rdns.length === 0) && rdns.length > 0) {
          existingWallet.rdns = rdns;
        }
        if (shouldPreferName(existingWallet.name, walletName)) {
          existingWallet.name = walletName;
        }
        return;
      }
    }

    const uuid = typeof info?.uuid === "string" ? info.uuid.trim() : "";
    const slug = walletName.toLowerCase().replace(/[^a-z0-9]+/g, "-");
    const baseId = uuid || rdns || family || (slug.length > 0 ? slug : "wallet");
    let candidateId = baseId;
    let suffix = 2;
    while (seenIds.has(candidateId) || (providerRegistry[candidateId] && providerRegistry[candidateId] !== provider)) {
      candidateId = baseId + "-" + suffix;
      suffix += 1;
    }

    seenIds.add(candidateId);
    providerRegistry[candidateId] = provider;
    const walletEntry = {
      id: candidateId,
      name: walletName,
      rdns: rdns || null,
      icon: icon || null,
    };
    discoveredWallets.push(walletEntry);
    if (identityKey.length > 0) {
      walletByIdentity.set(identityKey, walletEntry);
    }
  };

  const announceHandler = (event) => {
    const detail = event && event.detail ? event.detail : null;
    registerProvider(detail?.provider, detail?.info ?? {});
  };

  if (typeof global.addEventListener === "function") {
    global.addEventListener("eip6963:announceProvider", announceHandler);
  }

  try {
    if (typeof global.dispatchEvent === "function" && typeof Event === "function") {
      global.dispatchEvent(new Event("eip6963:requestProvider"));
    }
  } catch (_) {
  }

  await new Promise((resolve) => setTimeout(resolve, 220));

  if (typeof global.removeEventListener === "function") {
    global.removeEventListener("eip6963:announceProvider", announceHandler);
  }

  const injected = global.ethereum;
  if (injected && typeof injected.request === "function") {
    registerProvider(injected, {
      name: injected.isRabby
        ? "Rabby Wallet"
        : (injected.isCoinbaseWallet ? "Coinbase Wallet" : (injected.isMetaMask ? "MetaMask" : "Injected Wallet")),
      rdns: injected.isRabby
        ? "io.rabby"
        : (injected.isCoinbaseWallet ? "com.coinbase.wallet" : (injected.isMetaMask ? "io.metamask" : "")),
      icon: typeof injected.icon === "string" ? injected.icon : "",
    });
    if (Array.isArray(injected.providers)) {
      for (const provider of injected.providers) {
        registerProvider(provider, {
          name: provider && provider.isRabby
            ? "Rabby Wallet"
            : (provider && provider.isCoinbaseWallet
              ? "Coinbase Wallet"
              : ((provider && provider.isMetaMask) ? "MetaMask" : "Injected Wallet")),
          rdns: provider && provider.isRabby
            ? "io.rabby"
            : (provider && provider.isCoinbaseWallet
              ? "com.coinbase.wallet"
              : ((provider && provider.isMetaMask) ? "io.metamask" : "")),
          icon: provider && typeof provider.icon === "string" ? provider.icon : "",
        });
      }
    }
  }

  for (const wallet of discoveredWallets) {
    wallet.icon = await normalizeIconToPngDataUrl(wallet.icon);
  }

  return JSON.stringify(discoveredWallets);
}
"""
)
private external fun discoverWalletsJson(): Promise<JsAny?>

@JsFun(
    """
async (walletId) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.request !== "function") {
    return "[]";
  }
  try {
    const accounts = await provider.request({ method: "eth_accounts" });
    if (!Array.isArray(accounts)) {
      return "[]";
    }
    return JSON.stringify(accounts.map((account) => String(account)));
  } catch (_) {
    return "[]";
  }
}
"""
)
private external fun getConnectedWalletAccountsJson(walletId: String): Promise<JsAny?>

@JsFun(
    """
(walletId, watcherId, listener) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.on !== "function") {
    return false;
  }
  const listenersRegistry = globalThis.__bpWalletSessionListeners ?? (globalThis.__bpWalletSessionListeners = {});
  const storageKey = String(walletId) + "::" + String(watcherId);
  if (listenersRegistry[storageKey]) {
    return true;
  }
  const safeEmit = (eventType, walletAddress) => {
    try {
      listener(String(eventType), typeof walletAddress === "string" ? walletAddress : "");
    } catch (_) {
    }
  };
  const onAccountsChanged = (accounts) => {
    const firstAccount = Array.isArray(accounts) && accounts.length > 0 ? String(accounts[0]) : "";
    safeEmit("accountsChanged", firstAccount);
  };
  const onChainChanged = () => {
    safeEmit("chainChanged", "");
  };
  const onDisconnect = () => {
    safeEmit("disconnect", "");
  };
  provider.on("accountsChanged", onAccountsChanged);
  provider.on("chainChanged", onChainChanged);
  provider.on("disconnect", onDisconnect);
  listenersRegistry[storageKey] = {
    provider,
    onAccountsChanged,
    onChainChanged,
    onDisconnect,
  };
  return true;
}
"""
)
private external fun registerWalletSessionListener(
    walletId: String,
    watcherId: String,
    listener: (String, String) -> Unit,
): Boolean

@JsFun(
    """
(walletId, watcherId) => {
  const listenersRegistry = globalThis.__bpWalletSessionListeners || {};
  const storageKey = String(walletId) + "::" + String(watcherId);
  const entry = listenersRegistry[storageKey];
  if (!entry) {
    return;
  }
  const provider = entry.provider;
  const remove = typeof provider?.removeListener === "function"
    ? provider.removeListener.bind(provider)
    : (typeof provider?.off === "function" ? provider.off.bind(provider) : null);
  if (remove) {
    try { remove("accountsChanged", entry.onAccountsChanged); } catch (_) {}
    try { remove("chainChanged", entry.onChainChanged); } catch (_) {}
    try { remove("disconnect", entry.onDisconnect); } catch (_) {}
  }
  delete listenersRegistry[storageKey];
}
"""
)
private external fun unregisterWalletSessionListener(
    walletId: String,
    watcherId: String,
)

@JsFun(
    """
async (walletId, targetChainId, targetNetworkName) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.request !== "function") {
    throw new Error("Selected wallet is not available. Refresh wallet list.");
  }
  const toNumericChainId = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
  };
  const toHexChainId = (value) => "0x" + Number(value).toString(16);
  const readErrorCode = (error) => {
    if (error && typeof error.code === "number") {
      return error.code;
    }
    if (error && typeof error.originalError?.code === "number") {
      return error.originalError.code;
    }
    if (error && typeof error.data?.originalError?.code === "number") {
      return error.data.originalError.code;
    }
    return null;
  };
  const requiredChainId = toNumericChainId(targetChainId);
  const accounts = await provider.request({ method: "eth_requestAccounts" });
  if (!Array.isArray(accounts) || accounts.length === 0) {
    throw new Error("No wallet account available.");
  }
  const walletAddress = String(accounts[0]);
  let chainIdHex = await provider.request({ method: "eth_chainId" });
  let parsed = Number.parseInt(String(chainIdHex), 16);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error("Invalid chain ID from wallet.");
  }
  if (requiredChainId > 0 && parsed !== requiredChainId) {
    try {
      await provider.request({
        method: "wallet_switchEthereumChain",
        params: [{ chainId: toHexChainId(requiredChainId) }],
      });
    } catch (error) {
      const code = readErrorCode(error);
      if (code === 4902) {
        const networkLabel = targetNetworkName ? String(targetNetworkName) : ("chain " + requiredChainId);
        throw new Error("Wallet does not know " + networkLabel + ". Add this network in the wallet first.");
      } else {
        throw error;
      }
    }
    chainIdHex = await provider.request({ method: "eth_chainId" });
    parsed = Number.parseInt(String(chainIdHex), 16);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      throw new Error("Invalid chain ID from wallet after network switch.");
    }
    if (parsed !== requiredChainId) {
      throw new Error("Wallet did not switch to the required network.");
    }
  }
  return JSON.stringify({
    walletAddress,
    chainId: parsed,
  });
}
"""
)
private external fun requestWalletLoginContextJson(
    walletId: String,
    targetChainId: Long,
    targetNetworkName: String,
): Promise<JsAny?>

@JsFun(
    """
async (walletId, walletAddress, message) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.request !== "function") {
    throw new Error("Selected wallet is not available. Refresh wallet list.");
  }
  const signature = await provider.request({
    method: "personal_sign",
    params: [message, walletAddress]
  });
  return String(signature);
}
"""
)
private external fun signPersonalMessage(
    walletId: String,
    walletAddress: String,
    message: String,
): Promise<JsAny?>

@JsFun(
    """
async (walletId, walletAddress, to, data, valueHex) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.request !== "function") {
    throw new Error("Selected wallet is not available. Refresh wallet list.");
  }
  const txHash = await provider.request({
    method: "eth_sendTransaction",
    params: [{
      from: String(walletAddress),
      to: String(to),
      data: String(data),
      value: String(valueHex || "0x0"),
    }],
  });
  return String(txHash);
}
"""
)
private external fun sendWalletTransaction(
    walletId: String,
    walletAddress: String,
    to: String,
    data: String,
    valueHex: String,
): Promise<JsAny?>

@JsFun(
    """
async (walletId, txHash) => {
  const providerRegistry = globalThis.__bpWalletProviders || {};
  const provider = providerRegistry[walletId];
  if (!provider || typeof provider.request !== "function") {
    throw new Error("Selected wallet is not available. Refresh wallet list.");
  }
  const startedAt = Date.now();
  while (Date.now() - startedAt <= 180000) {
    const receipt = await provider.request({
      method: "eth_getTransactionReceipt",
      params: [String(txHash)],
    });
    if (receipt && typeof receipt === "object") {
      return JSON.stringify({
        transactionHash: String(receipt.transactionHash || txHash),
        status: receipt.status == null ? null : String(receipt.status),
      });
    }
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  throw new Error("Transaction receipt was not confirmed in time.");
}
"""
)
private external fun waitForWalletTransactionReceipt(
    walletId: String,
    txHash: String,
): Promise<JsAny?>

@JsFun(
    """
(value) => {
  if (value == null) {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  const message = value && typeof value.message === "string" ? value.message.trim() : "";
  if (message.length > 0) {
    return message;
  }
  try {
    return JSON.stringify(value);
  } catch (_) {
    return String(value);
  }
}
"""
)
private external fun jsAnyToString(value: JsAny?): String

private fun kotlinx.serialization.json.JsonObject.requiredString(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalStateException("Missing '$name' in wallet payload.")
}
