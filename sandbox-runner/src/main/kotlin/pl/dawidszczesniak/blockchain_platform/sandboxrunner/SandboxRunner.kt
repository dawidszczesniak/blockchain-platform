package pl.dawidszczesniak.blockchain_platform.sandboxrunner

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

private const val MAX_BODY_BYTES: Int = 512 * 1024
private const val MAX_TESTS: Int = 200
private const val MAX_TIMEOUT_MS: Int = 60_000
private const val MIN_TIMEOUT_MS: Int = 50
private const val MAX_VALIDATOR_TIMEOUT_MS: Int = 5_000
private const val MAX_MEMORY_LIMIT_MB: Int = 2048
private const val MIN_MEMORY_LIMIT_MB: Int = 16
private const val MAX_INPUT_CHARS: Int = 64_000
private const val MAX_EXPECTED_OUTPUT_CHARS: Int = 64_000
private const val MAX_VALIDATOR_CODE_CHARS: Int = 120_000
private const val WORKER_WARMUP_ITERATIONS: Int = 25
private const val WORKER_PROTOCOL_STDERR_LIMIT_BYTES: Int = 1_048_576
private const val POLL_INTERVAL_MILLIS: Long = 25
private const val WORKER_STDOUT_EOF_SENTINEL: String = "__WORKER_STDOUT_EOF__"

private val nodeId: String = System.getenv("SANDBOX_NODE_ID") ?: "sandbox-node"
private val imageHash: String = System.getenv("SANDBOX_IMAGE_HASH") ?: "unknown"
private val sandboxVersion: String = System.getenv("SANDBOX_VERSION") ?: "1"
private val attestationSecret: String = System.getenv("SANDBOX_ATTESTATION_SECRET") ?: ""
private val host: String = System.getenv("SANDBOX_HOST") ?: "0.0.0.0"
private val port: Int = (System.getenv("SANDBOX_PORT") ?: "8080").toInt()
private val javaBinary: String = System.getenv("JAVA_BIN")?.takeIf { it.isNotBlank() } ?: "java"
private val javacBinary: String = System.getenv("JAVAC_BIN")?.takeIf { it.isNotBlank() } ?: "javac"
private val kotlinBinary: String = System.getenv("KOTLIN_BIN")?.takeIf { it.isNotBlank() } ?: "kotlin"
private val kotlincBinary: String = System.getenv("KOTLINC_BIN")?.takeIf { it.isNotBlank() } ?: "kotlinc"

private val runnerFileHash: String = computeRunnerFileHash()
private val activeRuns: MutableMap<String, RunControl> = Collections.synchronizedMap(mutableMapOf())

private data class NormalizedTest(
    val id: Int,
    val order: Int,
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

private data class ProcessExecution(
    val timedOut: Boolean,
    val returnCode: Int?,
    val stdout: String,
    val stderr: String,
)

private sealed interface CompileResult {
    data class Success(
        val userClassesPath: Path,
        val harnessClassesPath: Path,
        val command: List<String>,
    ) : CompileResult

    data class Failure(
        val message: String,
    ) : CompileResult
}

private data class WorkerSession(
    val process: Process,
    val stdoutQueue: LinkedBlockingQueue<String?>,
    val stderrBuffer: BoundedTextBuffer,
    val stdoutThread: Thread,
    val stderrThread: Thread,
    var registered: Boolean,
    var closed: Boolean = false,
)

private class RunCancelledException(
    message: String,
) : RuntimeException(message)

private class RunControl {
    private val processes = mutableSetOf<Process>()
    private val lock = Any()
    @Volatile
    private var cancelled: Boolean = false

    fun registerProcess(process: Process) {
        synchronized(lock) {
            if (cancelled) {
                throw RunCancelledException("Run cancelled.")
            }
            processes += process
        }
    }

    fun unregisterProcess(process: Process) {
        synchronized(lock) {
            processes -= process
        }
    }

    fun throwIfCancelled() {
        if (cancelled) {
            throw RunCancelledException("Run cancelled.")
        }
    }

    fun isCancelled(): Boolean = cancelled

    fun cancel() {
        val snapshot: List<Process> = synchronized(lock) {
            if (cancelled) {
                return
            }
            cancelled = true
            processes.toList()
        }
        snapshot.forEach { process ->
            runCatching {
                process.destroyForcibly()
            }
        }
    }
}

private class BoundedTextBuffer(
    private val maxBytes: Int,
) {
    private val builder = StringBuilder()
    private var currentBytes: Int = 0
    private val lock = Any()

    fun append(value: String) {
        synchronized(lock) {
            if (currentBytes >= maxBytes || value.isEmpty()) {
                return
            }
            val encoded = value.toByteArray(StandardCharsets.UTF_8)
            val remaining = maxBytes - currentBytes
            if (remaining <= 0) {
                return
            }
            if (encoded.size <= remaining) {
                builder.append(value)
                currentBytes += encoded.size
                return
            }
            val truncated = encoded.copyOf(remaining).toString(StandardCharsets.UTF_8)
            builder.append(truncated)
            currentBytes += truncated.toByteArray(StandardCharsets.UTF_8).size
        }
    }

    fun snapshot(): String = synchronized(lock) { builder.toString() }

    fun clear() {
        synchronized(lock) {
            builder.setLength(0)
            currentBytes = 0
        }
    }
}

private fun computeRunnerFileHash(): String {
    val codeSource = SandboxRunner::class.java.protectionDomain?.codeSource?.location ?: return sha256Hex("unknown")
    return runCatching {
        sha256Hex(File(codeSource.toURI()).readBytes())
    }.getOrElse { sha256Hex("unknown") }
}

private object SandboxRunner

fun main() {
    val server = HttpServer.create(InetSocketAddress(host, port), 0)
    server.createContext("/health") { exchange ->
        if (exchange.requestMethod != "GET") {
            sendJsonResponse(exchange, 405, linkedMapOf("message" to "Method not allowed."))
            return@createContext
        }
        sendJsonResponse(exchange, 200, linkedMapOf("status" to "ok", "nodeId" to nodeId))
    }
    server.createContext("/attestation") { exchange ->
        if (exchange.requestMethod != "GET") {
            sendJsonResponse(exchange, 405, linkedMapOf("message" to "Method not allowed."))
            return@createContext
        }
        sendJsonResponse(
            exchange,
            200,
            linkedMapOf(
                "nodeId" to nodeId,
                "imageHash" to imageHash,
                "sandboxVersion" to sandboxVersion,
                "runnerFileHash" to runnerFileHash,
            ),
        )
    }
    server.createContext("/cancel") { exchange ->
        if (exchange.requestMethod != "POST") {
            sendJsonResponse(exchange, 405, linkedMapOf("message" to "Method not allowed."))
            return@createContext
        }
        try {
            val payload = readJsonObjectBody(exchange)
            val runId = payload.string("runId").trim()
            if (runId.isEmpty()) {
                sendJsonResponse(exchange, 400, linkedMapOf("message" to "runId is required."))
                return@createContext
            }
            sendJsonResponse(exchange, 202, linkedMapOf("cancelled" to cancelRun(runId)))
        } catch (error: IllegalArgumentException) {
            sendJsonResponse(exchange, 400, linkedMapOf("message" to (error.message ?: "Invalid request.")))
        } catch (error: Exception) {
            sendJsonResponse(exchange, 500, linkedMapOf("message" to "Sandbox execution error: ${error.message}"))
        }
    }
    server.createContext("/run") { exchange ->
        if (exchange.requestMethod != "POST") {
            sendJsonResponse(exchange, 405, linkedMapOf("message" to "Method not allowed."))
            return@createContext
        }
        try {
            val payload = readJsonObjectBody(exchange)
            val result = runPayload(payload)
            sendJsonResponse(exchange, 200, result)
        } catch (error: IllegalArgumentException) {
            sendJsonResponse(exchange, 400, linkedMapOf("message" to (error.message ?: "Invalid request.")))
        } catch (error: RunCancelledException) {
            sendJsonResponse(exchange, 409, linkedMapOf("message" to (error.message ?: "Run cancelled.")))
        } catch (error: Exception) {
            sendJsonResponse(exchange, 500, linkedMapOf("message" to "Sandbox execution error: ${error.message}"))
        }
    }
    server.executor = Executors.newCachedThreadPool()
    server.start()
}

object SandboxRunnerTestApi {
    @JvmStatic
    fun runPayloadJson(rawJson: String): String {
        val payload = JsonObject.from(MiniJson.parse(rawJson))
        return MiniJson.stringify(runPayload(payload))
    }

    @JvmStatic
    fun cancelRunForTesting(runId: String): Boolean = cancelRun(runId)
}

private fun readJsonObjectBody(exchange: HttpExchange): JsonObject {
    val contentLength = exchange.requestHeaders.getFirst("Content-Length")?.toIntOrNull() ?: 0
    if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
        throw IllegalArgumentException("Invalid request size.")
    }
    val raw = exchange.requestBody.use { stream ->
        stream.readNBytes(contentLength)
    }
    val parsed = MiniJson.parse(raw.toString(StandardCharsets.UTF_8))
    return JsonObject.from(parsed)
}

private fun sendJsonResponse(
    exchange: HttpExchange,
    status: Int,
    payload: Any?,
) {
    val raw = MiniJson.stringify(payload).toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, raw.size.toLong())
    exchange.responseBody.use { body ->
        body.write(raw)
    }
}

private fun registerRun(runId: String): RunControl {
    val control = RunControl()
    activeRuns[runId] = control
    return control
}

private fun finishRun(runId: String, control: RunControl) {
    synchronized(activeRuns) {
        if (activeRuns[runId] === control) {
            activeRuns.remove(runId)
        }
    }
}

private fun cancelRun(runId: String): Boolean {
    val control = synchronized(activeRuns) { activeRuns.remove(runId) } ?: return false
    control.cancel()
    return true
}

private fun runPayload(payload: JsonObject): LinkedHashMap<String, Any?> {
    val runId = payload.stringOrNull("runId")?.trim().orEmpty().ifEmpty { null }
    val sourceCode = payload.string("sourceCode").trim()
    if (sourceCode.isEmpty()) {
        throw IllegalArgumentException("sourceCode is required.")
    }
    val language = payload.stringOrNull("language")?.trim()?.lowercase(Locale.getDefault()).orEmpty().ifEmpty { "kotlin" }

    val testsArray = payload.array("tests")
    if (testsArray.isEmpty()) {
        throw IllegalArgumentException("tests must be a non-empty array.")
    }
    if (testsArray.size > MAX_TESTS) {
        throw IllegalArgumentException("Too many tests. Maximum is $MAX_TESTS.")
    }

    val normalizedTests = testsArray.map { rawTest ->
        val test = JsonObject.from(rawTest)
        val inputData = test.stringOrNull("inputData").orEmpty()
        val expectedOutput = test.stringOrNull("expectedOutput").orEmpty()
        val validatorCode = test.stringOrNull("validatorCode").orEmpty()
        val validatorLanguage = test.stringOrNull("validatorLanguage")
            ?.trim()
            ?.lowercase(Locale.getDefault())
            .orEmpty()
            .ifEmpty { "kotlin" }

        if (inputData.length > MAX_INPUT_CHARS) {
            throw IllegalArgumentException("Test inputData is too large. Max $MAX_INPUT_CHARS characters.")
        }
        if (expectedOutput.length > MAX_EXPECTED_OUTPUT_CHARS) {
            throw IllegalArgumentException("Test expectedOutput is too large. Max $MAX_EXPECTED_OUTPUT_CHARS characters.")
        }
        if (validatorCode.length > MAX_VALIDATOR_CODE_CHARS) {
            throw IllegalArgumentException("Test validatorCode is too large. Max $MAX_VALIDATOR_CODE_CHARS characters.")
        }

        NormalizedTest(
            id = test.int("id"),
            order = test.int("order"),
            inputData = inputData,
            expectedOutput = expectedOutput,
            validatorCode = validatorCode,
            validatorLanguage = validatorLanguage,
            timeoutMs = normalizeTimeoutMs(test.intOrNull("timeoutMs") ?: 1000),
            memoryLimitMb = normalizeMemoryLimitMb(test.intOrNull("memoryLimitMb") ?: 256),
        )
    }

    val runHash = computeRunHash(
        linkedMapOf(
            "sourceCode" to sourceCode,
            "language" to language,
            "tests" to normalizedTests.map { test ->
                linkedMapOf(
                    "id" to test.id,
                    "order" to test.order,
                    "inputData" to test.inputData,
                    "expectedOutput" to test.expectedOutput,
                    "validatorCode" to test.validatorCode,
                    "validatorLanguage" to test.validatorLanguage,
                    "timeoutMs" to test.timeoutMs,
                    "memoryLimitMb" to test.memoryLimitMb,
                )
            },
        ),
    )

    val runControl = runId?.let(::registerRun)
    try {
        runControl?.throwIfCancelled()
        val workdir = Files.createTempDirectory("sandbox_run_")
        try {
            val compiled = compileSolution(workdir, sourceCode, language, runControl)
            var suiteExecutionTimeMs = 0
            val results: List<LinkedHashMap<String, Any?>> = when (compiled) {
                is CompileResult.Failure -> normalizedTests.map { test ->
                    linkedMapOf(
                        "id" to test.id,
                        "order" to test.order,
                        "status" to "ERROR",
                        "output" to null,
                        "passed" to false,
                        "executionTimeMs" to 0,
                        "memoryUsedKb" to null,
                        "message" to compiled.message,
                    )
                }
                is CompileResult.Success -> {
                    val validatorJarsByKey = linkedMapOf<String, String>()
                    val validatorCompileErrors = linkedMapOf<String, String>()
                    val uniqueValidators = linkedMapOf<String, Triple<String, String, String>>()
                    normalizedTests.forEach { test ->
                        if (test.validatorCode.isNotEmpty()) {
                            val validatorHash = sha256Hex(test.validatorCode)
                            val key = "${test.validatorLanguage}:$validatorHash"
                            uniqueValidators.putIfAbsent(key, Triple(test.validatorCode, test.validatorLanguage, validatorHash))
                        }
                    }
                    uniqueValidators.forEach { (key, triple) ->
                        runControl?.throwIfCancelled()
                        val compiledValidator = compileValidator(
                            workdir = workdir,
                            validatorCode = triple.first,
                            validatorLanguage = triple.second,
                            validatorHash = triple.third,
                            runControl = runControl,
                        )
                        when (compiledValidator) {
                            is CompileResult.Success -> validatorJarsByKey[key] = compiledValidator.command.last()
                            is CompileResult.Failure -> validatorCompileErrors[key] = compiledValidator.message
                        }
                    }

                    val workerCommand = buildWorkerCommand(
                        compiled.command,
                        normalizedTests.maxOf { it.memoryLimitMb },
                    )

                    val startedAt = System.nanoTime()
                    val workerResults = mutableListOf<LinkedHashMap<String, Any?>>()
                    var workerSession: WorkerSession? = null
                    try {
                        normalizedTests.forEach { test ->
                            runControl?.throwIfCancelled()
                            val currentSession = workerSession
                            if (currentSession == null || !currentSession.process.isAlive || currentSession.closed) {
                                currentSession?.let { stopWorker(it, graceful = false, runControl = runControl) }
                                workerSession = startWorker(workerCommand, workdir, runControl)
                            }
                            val activeSession = requireNotNull(workerSession)
                            val execution = executeTestWithWorker(activeSession, test, runControl)
                            workerResults += execution
                            if (activeSession.closed || !activeSession.process.isAlive) {
                                stopWorker(activeSession, graceful = false, runControl = runControl)
                                workerSession = null
                            }
                        }
                    } finally {
                        workerSession?.let { stopWorker(it, graceful = true, runControl = runControl) }
                    }
                    suiteExecutionTimeMs = elapsedMilliseconds(startedAt)
                    workerResults.mapIndexed { index, execution ->
                        judgeTestResult(
                            execution = execution,
                            test = normalizedTests[index],
                            validatorJarsByKey = validatorJarsByKey,
                            validatorCompileErrors = validatorCompileErrors,
                            runControl = runControl,
                        )
                    }
                }
            }

            val resultHash = computeResultHash(results)
            val executedAt = Instant.now().toString()
            val attestationPayloadHash = computeAttestationPayloadHash(
                nodeId = nodeId,
                imageHash = imageHash,
                runHash = runHash,
                resultHash = resultHash,
                executedAt = executedAt,
            )
            return linkedMapOf(
                "nodeId" to nodeId,
                "imageHash" to imageHash,
                "runHash" to runHash,
                "resultHash" to resultHash,
                "suiteExecutionTimeMs" to suiteExecutionTimeMs,
                "executedAt" to executedAt,
                "attestationPayloadHash" to attestationPayloadHash,
                "attestationSignature" to signAttestation(attestationPayloadHash),
                "attestationScheme" to "hmac-sha256",
                "results" to results,
            )
        } finally {
            workdir.toFile().deleteRecursively()
        }
    } finally {
        if (runId != null && runControl != null) {
            finishRun(runId, runControl)
        }
    }
}

private fun compileSolution(
    workdir: Path,
    sourceCode: String,
    language: String,
    runControl: RunControl?,
): CompileResult {
    val normalizedLanguage = language.trim().lowercase(Locale.getDefault())
    val userClassesPath = workdir.resolve("user_classes")
    val harnessClassesPath = workdir.resolve("worker_harness_classes")
    Files.createDirectories(userClassesPath)
    Files.createDirectories(harnessClassesPath)

    when (normalizedLanguage) {
        "kotlin" -> {
            val sourcePath = workdir.resolve("Solution.kt")
            sourcePath.writeText(sourceCode)
            val process = runSubprocess(
                command = listOf(kotlincBinary, sourcePath.toString(), "-d", userClassesPath.toString()),
                cwd = workdir,
                runControl = runControl,
            )
            if (process.returnCode != 0) {
                return CompileResult.Failure((process.stderr.ifBlank { process.stdout }.ifBlank { "Compilation failed." }).trim())
            }
        }
        "java" -> {
            val sourcePath = workdir.resolve("Solution.java")
            sourcePath.writeText(sourceCode)
            val process = runSubprocess(
                command = listOf(javacBinary, "-d", userClassesPath.toString(), sourcePath.toString()),
                cwd = workdir,
                runControl = runControl,
            )
            if (process.returnCode != 0) {
                return CompileResult.Failure((process.stderr.ifBlank { process.stdout }.ifBlank { "Compilation failed." }).trim())
            }
        }
        else -> return CompileResult.Failure("Unsupported language '$language'.")
    }

    val harnessPath = workdir.resolve("WorkerHarness.kt")
    harnessPath.writeText(WORKER_HARNESS_CODE)
    val harnessCompile = runSubprocess(
        command = listOf(kotlincBinary, harnessPath.toString(), "-d", harnessClassesPath.toString()),
        cwd = workdir,
        runControl = runControl,
    )
    if (harnessCompile.returnCode != 0) {
        return CompileResult.Failure(
            (harnessCompile.stderr.ifBlank { harnessCompile.stdout }.ifBlank { "Worker harness compilation failed." }).trim(),
        )
    }

    return CompileResult.Success(
        userClassesPath = userClassesPath,
        harnessClassesPath = harnessClassesPath,
        command = listOf(kotlinBinary, "-classpath", harnessClassesPath.toString(), "WorkerHarnessKt", userClassesPath.toString()),
    )
}

private fun compileValidator(
    workdir: Path,
    validatorCode: String,
    validatorLanguage: String,
    validatorHash: String,
    runControl: RunControl?,
): CompileResult {
    if (validatorLanguage.lowercase(Locale.getDefault()) != "kotlin") {
        return CompileResult.Failure("Unsupported validator language '$validatorLanguage'.")
    }

    val validatorSource = workdir.resolve("Validator_$validatorHash.kt")
    val validatorHarness = workdir.resolve("ValidatorHarness_$validatorHash.kt")
    val validatorJar = workdir.resolve("validator_$validatorHash.jar")
    validatorSource.writeText(validatorCode)
    validatorHarness.writeText(VALIDATOR_HARNESS_CODE)

    val process = runSubprocess(
        command = listOf(
            kotlincBinary,
            validatorSource.toString(),
            validatorHarness.toString(),
            "-include-runtime",
            "-d",
            validatorJar.toString(),
        ),
        cwd = workdir,
        runControl = runControl,
    )
    if (process.returnCode != 0) {
        return CompileResult.Failure((process.stderr.ifBlank { process.stdout }.ifBlank { "Validator compilation failed." }).trim())
    }
    return CompileResult.Success(
        userClassesPath = validatorJar,
        harnessClassesPath = validatorJar,
        command = listOf(javaBinary, "-jar", validatorJar.toString()),
    )
}

private fun runSubprocess(
    command: List<String>,
    cwd: Path,
    inputText: String? = null,
    timeoutSeconds: Double? = null,
    runControl: RunControl?,
): ProcessExecution {
    val process = ProcessBuilder(command)
        .directory(cwd.toFile())
        .redirectErrorStream(false)
        .start()

    runControl?.registerProcess(process)

    val stdoutBuffer = BoundedTextBuffer(MAX_BODY_BYTES)
    val stderrBuffer = BoundedTextBuffer(MAX_BODY_BYTES)
    val stdoutThread = startStreamReader(process.inputStream, stdoutBuffer)
    val stderrThread = startStreamReader(process.errorStream, stderrBuffer)

    val stdinThread = inputText?.let { text ->
        thread(start = true, isDaemon = true) {
            process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(text)
                writer.flush()
            }
        }
    }

    val startedAt = System.nanoTime()
    var timedOut = false
    try {
        while (!process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
            if (runControl?.isCancelled() == true) {
                process.destroyForcibly()
                throw RunCancelledException("Run cancelled.")
            }
            if (timeoutSeconds != null && elapsedMilliseconds(startedAt) >= timeoutSeconds * 1000.0) {
                timedOut = true
                process.destroyForcibly()
                break
            }
        }
        stdinThread?.join(200)
        stdoutThread.join(200)
        stderrThread.join(200)
        if (runControl?.isCancelled() == true) {
            throw RunCancelledException("Run cancelled.")
        }
        return ProcessExecution(
            timedOut = timedOut,
            returnCode = runCatching { process.exitValue() }.getOrNull(),
            stdout = stdoutBuffer.snapshot(),
            stderr = stderrBuffer.snapshot(),
        )
    } finally {
        runControl?.unregisterProcess(process)
        process.outputStream.closeQuietly()
        process.inputStream.closeQuietly()
        process.errorStream.closeQuietly()
    }
}

private fun buildWorkerCommand(
    baseCommand: List<String>,
    memoryLimitMb: Int,
): List<String> {
    val command = baseCommand.toMutableList()
    when (command.firstOrNull()) {
        kotlinBinary -> command.add(1, "-J-Xmx${memoryLimitMb}m")
        javaBinary -> command.add(1, "-Xmx${memoryLimitMb}m")
    }
    return command
}

private fun startWorker(
    command: List<String>,
    cwd: Path,
    runControl: RunControl?,
): WorkerSession {
    val process = ProcessBuilder(command)
        .directory(cwd.toFile())
        .redirectErrorStream(false)
        .start()
    runControl?.registerProcess(process)

    val stdoutQueue = LinkedBlockingQueue<String?>()
    val stderrBuffer = BoundedTextBuffer(WORKER_PROTOCOL_STDERR_LIMIT_BYTES)
    val stdoutThread = thread(start = true, isDaemon = true) {
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stdoutQueue.put(line!!)
            }
        }
        stdoutQueue.put(WORKER_STDOUT_EOF_SENTINEL)
    }
    val stderrThread = startStreamReader(process.errorStream, stderrBuffer)

    return WorkerSession(
        process = process,
        stdoutQueue = stdoutQueue,
        stderrBuffer = stderrBuffer,
        stdoutThread = stdoutThread,
        stderrThread = stderrThread,
        registered = runControl != null,
    )
}

private fun stopWorker(
    session: WorkerSession,
    graceful: Boolean,
    runControl: RunControl?,
) {
    if (session.closed) {
        return
    }
    session.closed = true
    try {
        if (graceful && session.process.isAlive) {
            runCatching {
                session.process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write("""{"type":"SHUTDOWN"}""")
                    writer.write('\n'.code)
                    writer.flush()
                }
            }
            session.process.waitFor(200, TimeUnit.MILLISECONDS)
        }
        if (session.process.isAlive) {
            session.process.destroyForcibly()
            session.process.waitFor(500, TimeUnit.MILLISECONDS)
        }
    } finally {
        if (runControl != null && session.registered) {
            runControl.unregisterProcess(session.process)
            session.registered = false
        }
        session.process.outputStream.closeQuietly()
        session.process.inputStream.closeQuietly()
        session.process.errorStream.closeQuietly()
        session.stdoutThread.join(200)
        session.stderrThread.join(200)
    }
}

private fun executeTestWithWorker(
    session: WorkerSession,
    test: NormalizedTest,
    runControl: RunControl?,
): LinkedHashMap<String, Any?> {
    if (!session.process.isAlive) {
        return linkedMapOf(
            "id" to test.id,
            "order" to test.order,
            "status" to "ERROR",
            "output" to null,
            "passed" to false,
            "executionTimeMs" to 0,
            "memoryUsedKb" to null,
            "message" to session.stderrBuffer.snapshot().ifBlank { "Worker process exited unexpectedly." },
        )
    }

    session.stderrBuffer.clear()

    val request = linkedMapOf(
        "type" to "RUN_TEST",
        "testId" to test.id,
        "inputBase64" to Base64.getEncoder().encodeToString(test.inputData.toByteArray(StandardCharsets.UTF_8)),
        "warmupIterations" to WORKER_WARMUP_ITERATIONS,
    )

    try {
        synchronized(session.process) {
            val writer = session.process.outputStream.writer(StandardCharsets.UTF_8)
            writer.write(MiniJson.stringify(request))
            writer.write("\n")
            writer.flush()
        }
    } catch (_: Exception) {
        // fall through to error below by inspecting worker state and stderr
    }

    val startedAt = System.nanoTime()
    while (true) {
        runControl?.throwIfCancelled()
        val line = session.stdoutQueue.poll(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
        if (line != null) {
            if (runControl?.isCancelled() == true) {
                stopWorker(session, graceful = false, runControl = runControl)
                throw RunCancelledException("Run cancelled.")
            }
            if (line == WORKER_STDOUT_EOF_SENTINEL) {
                if (runControl?.isCancelled() == true) {
                    stopWorker(session, graceful = false, runControl = runControl)
                    throw RunCancelledException("Run cancelled.")
                }
                stopWorker(session, graceful = false, runControl = runControl)
                return linkedMapOf(
                    "id" to test.id,
                    "order" to test.order,
                    "status" to "ERROR",
                    "output" to null,
                    "passed" to false,
                    "executionTimeMs" to 0,
                    "memoryUsedKb" to null,
                    "message" to session.stderrBuffer.snapshot().ifBlank { "Worker process exited unexpectedly." },
                )
            }
            return parseWorkerResponse(line, test, session, runControl)
        }
        if (!session.process.isAlive) {
            if (runControl?.isCancelled() == true) {
                stopWorker(session, graceful = false, runControl = runControl)
                throw RunCancelledException("Run cancelled.")
            }
            stopWorker(session, graceful = false, runControl = runControl)
            return linkedMapOf(
                "id" to test.id,
                "order" to test.order,
                "status" to "ERROR",
                "output" to null,
                "passed" to false,
                "executionTimeMs" to 0,
                "memoryUsedKb" to null,
                "message" to session.stderrBuffer.snapshot().ifBlank { "Worker process exited unexpectedly." },
            )
        }
        if (elapsedMilliseconds(startedAt) >= test.timeoutMs) {
            stopWorker(session, graceful = false, runControl = runControl)
            return linkedMapOf(
                "id" to test.id,
                "order" to test.order,
                "status" to "TIMEOUT",
                "output" to null,
                "passed" to false,
                "executionTimeMs" to test.timeoutMs,
                "memoryUsedKb" to null,
                "message" to "Execution timed out.",
            )
        }
    }
}

private fun parseWorkerResponse(
    line: String,
    test: NormalizedTest,
    session: WorkerSession,
    runControl: RunControl?,
): LinkedHashMap<String, Any?> {
    return try {
        val response = JsonObject.from(MiniJson.parse(line))
        if (response.string("type") != "TEST_RESULT") {
            throw IllegalArgumentException("Unexpected worker response type '${response.string("type")}'.")
        }
        if (response.int("testId") != test.id) {
            throw IllegalArgumentException("Worker response testId does not match the active test.")
        }
        val output = decodeWorkerText(response.stringOrNull("outputBase64"))
        val stderrText = decodeWorkerText(response.stringOrNull("stderrBase64"))
        val internalError = decodeWorkerText(response.stringOrNull("internalErrorBase64"))
        val executionTimeMs = executionTimeMsFromNs(response.long("executionTimeNs"))
        val memoryUsedKb = response.longOrNull("memoryUsedKb")
        when (response.string("status").uppercase(Locale.getDefault())) {
            "OK" -> linkedMapOf(
                "id" to test.id,
                "order" to test.order,
                "status" to "OK",
                "output" to output,
                "passed" to false,
                "executionTimeMs" to executionTimeMs,
                "memoryUsedKb" to memoryUsedKb,
                "message" to null,
            )
            "ERROR" -> linkedMapOf(
                "id" to test.id,
                "order" to test.order,
                "status" to "ERROR",
                "output" to output.ifEmpty { null },
                "passed" to false,
                "executionTimeMs" to executionTimeMs,
                "memoryUsedKb" to memoryUsedKb,
                "message" to internalError.ifBlank {
                    stderrText.ifBlank {
                        output.ifBlank { "Execution failed." }
                    }
                }.trim(),
            )
            else -> {
                stopWorker(session, graceful = false, runControl = runControl)
                linkedMapOf(
                    "id" to test.id,
                    "order" to test.order,
                    "status" to "ERROR",
                    "output" to null,
                    "passed" to false,
                    "executionTimeMs" to 0,
                    "memoryUsedKb" to null,
                    "message" to "Unsupported worker status '${response.string("status")}'.",
                )
            }
        }
    } catch (error: Exception) {
        stopWorker(session, graceful = false, runControl = runControl)
        linkedMapOf(
            "id" to test.id,
            "order" to test.order,
            "status" to "ERROR",
            "output" to null,
            "passed" to false,
            "executionTimeMs" to 0,
            "memoryUsedKb" to null,
            "message" to (error.message ?: "Worker protocol failed."),
        )
    }
}

private fun executeValidator(
    validatorJarPath: String,
    test: NormalizedTest,
    actualOutput: String,
    runControl: RunControl?,
): LinkedHashMap<String, Any?> {
    val timeoutMs = minOf(test.timeoutMs, MAX_VALIDATOR_TIMEOUT_MS)
    val payload = buildString {
        append(Base64.getEncoder().encodeToString(test.inputData.toByteArray(StandardCharsets.UTF_8)))
        append('\n')
        append(Base64.getEncoder().encodeToString(test.expectedOutput.toByteArray(StandardCharsets.UTF_8)))
        append('\n')
        append(Base64.getEncoder().encodeToString(actualOutput.toByteArray(StandardCharsets.UTF_8)))
        append('\n')
    }
    val startedAt = System.nanoTime()
    return try {
        val result = runSubprocess(
            command = listOf(javaBinary, "-jar", validatorJarPath),
            cwd = File(validatorJarPath).parentFile.toPath(),
            inputText = payload,
            timeoutSeconds = timeoutMs / 1000.0,
            runControl = runControl,
        )
        val elapsedMs = elapsedMilliseconds(startedAt)
        when {
            result.timedOut -> linkedMapOf("ok" to false, "passed" to false, "message" to "Validator timed out.", "executionTimeMs" to elapsedMs)
            result.returnCode != 0 -> linkedMapOf(
                "ok" to false,
                "passed" to false,
                "message" to (result.stderr.ifBlank { result.stdout }.ifBlank { "Validator execution failed." }).trim(),
                "executionTimeMs" to elapsedMs,
            )
            result.stdout.trim().lowercase(Locale.getDefault()) == "true" -> linkedMapOf(
                "ok" to true,
                "passed" to true,
                "message" to null,
                "executionTimeMs" to elapsedMs,
            )
            result.stdout.trim().lowercase(Locale.getDefault()) == "false" -> linkedMapOf(
                "ok" to true,
                "passed" to false,
                "message" to "Validator returned false.",
                "executionTimeMs" to elapsedMs,
            )
            else -> linkedMapOf(
                "ok" to false,
                "passed" to false,
                "message" to "Validator returned invalid verdict '${result.stdout.trim()}'.",
                "executionTimeMs" to elapsedMs,
            )
        }
    } catch (error: RunCancelledException) {
        throw error
    } catch (error: Exception) {
        linkedMapOf(
            "ok" to false,
            "passed" to false,
            "message" to (error.message ?: "Validator execution failed."),
            "executionTimeMs" to elapsedMilliseconds(startedAt),
        )
    }
}

private fun judgeTestResult(
    execution: LinkedHashMap<String, Any?>,
    test: NormalizedTest,
    validatorJarsByKey: Map<String, String>,
    validatorCompileErrors: Map<String, String>,
    runControl: RunControl?,
): LinkedHashMap<String, Any?> {
    if (execution["status"] != "OK") {
        execution["passed"] = false
        return execution
    }

    if (test.validatorCode.isNotEmpty()) {
        val key = "${test.validatorLanguage}:${sha256Hex(test.validatorCode)}"
        val compileError = validatorCompileErrors[key]
        if (compileError != null) {
            execution["status"] = "ERROR"
            execution["passed"] = false
            execution["message"] = compileError
            return execution
        }
        val validatorJar = validatorJarsByKey[key]
        if (validatorJar == null) {
            execution["status"] = "ERROR"
            execution["passed"] = false
            execution["message"] = "Validator is not compiled."
            return execution
        }
        val verdict = executeValidator(
            validatorJarPath = validatorJar,
            test = test,
            actualOutput = execution["output"]?.toString().orEmpty(),
            runControl = runControl,
        )
        if (verdict["ok"] != true) {
            execution["status"] = "ERROR"
            execution["passed"] = false
            execution["message"] = verdict["message"]
            return execution
        }
        execution["passed"] = verdict["passed"] == true
        execution["message"] = if (verdict["passed"] == true) null else verdict["message"] ?: "Validator returned false."
        return execution
    }

    if (test.expectedOutput.isNotEmpty()) {
        val actual = normalizeOutput(execution["output"]?.toString().orEmpty())
        val expected = normalizeOutput(test.expectedOutput)
        execution["passed"] = actual == expected
        execution["message"] = if (actual == expected) null else "Output does not match expected value."
        return execution
    }

    execution["passed"] = false
    execution["message"] = "Test must define expectedOutput or validatorCode."
    return execution
}

private fun normalizeTimeoutMs(rawValue: Int): Int = rawValue.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

private fun normalizeMemoryLimitMb(rawValue: Int): Int = rawValue.coerceIn(MIN_MEMORY_LIMIT_MB, MAX_MEMORY_LIMIT_MB)

private fun normalizeOutput(value: String): String = value.replace("\r\n", "\n").trimEnd()

private fun computeRunHash(payload: Any?): String = "0x${sha256Hex(MiniJson.stringifyCanonical(payload))}"

private fun computeResultHash(results: List<LinkedHashMap<String, Any?>>): String {
    val canonical = results.sortedBy { (it["order"] as Number).toInt() }
        .joinToString("\n") { item ->
            listOf(
                (item["id"] as Number).toInt(),
                (item["order"] as Number).toInt(),
                item["status"].toString(),
                item["output"]?.toString().orEmpty(),
                (item["passed"] as Boolean).toString().lowercase(Locale.getDefault()),
                item["message"]?.toString().orEmpty(),
            ).joinToString("|")
        }
    return "0x${sha256Hex(canonical)}"
}

private fun computeAttestationPayloadHash(
    nodeId: String,
    imageHash: String,
    runHash: String,
    resultHash: String,
    executedAt: String,
): String = "0x${sha256Hex("$nodeId|$imageHash|$runHash|$resultHash|$executedAt")}"

private fun signAttestation(payloadHash: String): String? {
    if (attestationSecret.isBlank()) {
        return null
    }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(attestationSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    return "0x${mac.doFinal(payloadHash.toByteArray(StandardCharsets.UTF_8)).toHexString()}"
}

private fun sha256Hex(value: String): String = sha256Hex(value.toByteArray(StandardCharsets.UTF_8))

private fun sha256Hex(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value).toHexString()

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }

private fun decodeWorkerText(value: String?): String {
    if (value.isNullOrEmpty()) {
        return ""
    }
    return String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}

private fun executionTimeMsFromNs(executionTimeNs: Long): Int {
    require(executionTimeNs >= 0) { "Worker response contains a negative executionTimeNs value." }
    if (executionTimeNs == 0L) {
        return 0
    }
    return maxOf(1, Math.ceil(executionTimeNs / 1_000_000.0).toInt())
}

private fun elapsedMilliseconds(startedAtNs: Long): Int = ((System.nanoTime() - startedAtNs) / 1_000_000L).toInt()

private fun startStreamReader(
    stream: InputStream,
    sink: BoundedTextBuffer,
): Thread = thread(start = true, isDaemon = true) {
    stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sink.append(line!!)
            sink.append("\n")
        }
    }
}

private fun AutoCloseable.closeQuietly() {
    runCatching { close() }
}

private class JsonObject private constructor(
    private val values: Map<String, Any?>,
) {
    companion object {
        fun from(value: Any?): JsonObject {
            val map = value as? Map<*, *> ?: throw IllegalArgumentException("JSON object expected.")
            return JsonObject(
                map.entries.associate { entry ->
                    (entry.key as? String ?: throw IllegalArgumentException("JSON object key must be a string.")) to entry.value
                },
            )
        }
    }

    fun string(key: String): String = stringOrNull(key) ?: throw IllegalArgumentException("$key is required.")

    fun stringOrNull(key: String): String? = values[key] as? String

    fun int(key: String): Int = intOrNull(key) ?: throw IllegalArgumentException("$key is required.")

    fun intOrNull(key: String): Int? = when (val value = values[key]) {
        is Number -> value.toInt()
        else -> null
    }

    fun long(key: String): Long = longOrNull(key) ?: throw IllegalArgumentException("$key is required.")

    fun longOrNull(key: String): Long? = when (val value = values[key]) {
        is Number -> value.toLong()
        else -> null
    }

    fun array(key: String): List<Any?> = values[key] as? List<Any?> ?: throw IllegalArgumentException("$key must be an array.")
}
