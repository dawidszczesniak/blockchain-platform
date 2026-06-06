package pl.dawidszczesniak.blockchain_platform.sandboxrunner

internal val VALIDATOR_HARNESS_CODE: String = """
import java.util.Base64

private fun decode(encoded: String): String {
    return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
}

fun main() {
    val encodedInput = readLine() ?: ""
    val encodedExpected = readLine() ?: ""
    val encodedActual = readLine() ?: ""

    val input = decode(encodedInput)
    val expected = decode(encodedExpected)
    val actual = decode(encodedActual)

    val passed = try {
        validate(input, expected, actual)
    } catch (error: Throwable) {
        System.err.print(error.message ?: error::class.simpleName ?: "Validator error")
        kotlin.system.exitProcess(1)
    }
    print(if (passed) "true" else "false")
}
"""

internal val WORKER_HARNESS_CODE: String = """
import com.sun.management.ThreadMXBean
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val DEFAULT_WARMUP_ITERATIONS = 25
private const val MEASURED_ITERATIONS = 3
private const val GC_SETTLE_MILLIS = 50L
private const val MAX_STDOUT_BYTES = 1_048_576
private const val MAX_STDERR_BYTES = 1_048_576

private data class WorkerRequest(
    val type: String,
    val testId: Int? = null,
    val inputBase64: String? = null,
    val warmupIterations: Int = DEFAULT_WARMUP_ITERATIONS,
)

private data class WorkerResponse(
    val type: String = "TEST_RESULT",
    val testId: Int,
    val status: String,
    val outputBase64: String? = null,
    val stderrBase64: String? = null,
    val executionTimeNs: Long = 0L,
    val memoryUsedKb: Long? = null,
    val internalErrorBase64: String? = null,
)

private data class ExecutionResult(
    val output: String,
    val stderr: String,
)

private data class MeasuredRunSample(
    val status: String,
    val output: String,
    val stderr: String,
    val executionTimeNs: Long,
    val memoryUsedKb: Long?,
    val internalError: String? = null,
)

private class ExecutionFailureException(
    val output: String,
    val stderr: String,
    cause: Throwable,
) : RuntimeException(cause)

private class BoundedByteArrayOutputStream(
    private val maxBytes: Int,
) : OutputStream() {
    private val delegate = ByteArrayOutputStream()
    private var writtenBytes: Int = 0

    override fun write(b: Int) {
        if (writtenBytes >= maxBytes) {
            return
        }
        delegate.write(b)
        writtenBytes += 1
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (writtenBytes >= maxBytes || length <= 0) {
            return
        }
        val writable = minOf(length, maxBytes - writtenBytes)
        if (writable <= 0) {
            return
        }
        delegate.write(buffer, offset, writable)
        writtenBytes += writable
    }

    fun appendText(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun asUtf8String(): String {
        return delegate.toString(StandardCharsets.UTF_8.name())
    }
}

private val allocationBean: ThreadMXBean? = initializeAllocationBean()

private fun initializeAllocationBean(): ThreadMXBean? {
    val rawBean = ManagementFactory.getThreadMXBean()
    val bean = rawBean as? ThreadMXBean ?: return null
    return runCatching {
        if (bean.isThreadAllocatedMemorySupported && !bean.isThreadAllocatedMemoryEnabled) {
            bean.isThreadAllocatedMemoryEnabled = true
        }
        bean
    }.getOrNull()
}

private fun currentAllocatedBytes(): Long? {
    val bean = allocationBean ?: return null
    if (!bean.isThreadAllocatedMemorySupported) {
        return null
    }
    return runCatching { bean.getThreadAllocatedBytes(Thread.currentThread().id) }.getOrNull()
}

private fun decodeBase64(value: String): String {
    return String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}

private fun encodeBase64(value: String): String {
    return Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun extractJsonString(json: String, key: String): String? {
    val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"]*)\"")
    return pattern.find(json)?.groupValues?.getOrNull(1)
}

private fun extractJsonInt(json: String, key: String): Int? {
    val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(-?[0-9]+)")
    return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private fun parseRequest(rawLine: String): WorkerRequest {
    val type = extractJsonString(rawLine, "type")
        ?: throw IllegalArgumentException("Protocol request is missing 'type'.")
    return when (type) {
        "RUN_TEST" -> WorkerRequest(
            type = type,
            testId = extractJsonInt(rawLine, "testId")
                ?: throw IllegalArgumentException("RUN_TEST is missing 'testId'."),
            inputBase64 = extractJsonString(rawLine, "inputBase64")
                ?: throw IllegalArgumentException("RUN_TEST is missing 'inputBase64'."),
            warmupIterations = extractJsonInt(rawLine, "warmupIterations") ?: DEFAULT_WARMUP_ITERATIONS,
        )

        "SHUTDOWN" -> WorkerRequest(type = type)
        else -> throw IllegalArgumentException("Unsupported worker request type '${'$'}type'.")
    }
}

private fun responseToJson(response: WorkerResponse): String {
    fun appendJsonField(builder: StringBuilder, name: String, value: String?) {
        builder.append("\"").append(name).append("\":")
        if (value == null) {
            builder.append("null")
        } else {
            builder.append("\"").append(value).append("\"")
        }
    }

    return buildString {
        append("{")
        append("\"type\":\"").append(response.type).append("\"")
        append(",\"testId\":").append(response.testId)
        append(",\"status\":\"").append(response.status).append("\"")
        append(",\"executionTimeNs\":").append(response.executionTimeNs)
        append(",\"memoryUsedKb\":").append(response.memoryUsedKb ?: "null")
        append(",")
        appendJsonField(this, "outputBase64", response.outputBase64)
        append(",")
        appendJsonField(this, "stderrBase64", response.stderrBase64)
        append(",")
        appendJsonField(this, "internalErrorBase64", response.internalErrorBase64)
        append("}")
    }
}

private fun throwableToString(error: Throwable): String {
    val writer = StringWriter()
    error.printStackTrace(PrintWriter(writer))
    return writer.toString()
}

private fun rootCause(error: Throwable): Throwable {
    var current = error
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current
}

private sealed interface ExecutionPlan {
    fun execute(input: String): ExecutionResult
}

private class SolveExecutionPlan(
    private val method: Method,
) : ExecutionPlan {
    override fun execute(input: String): ExecutionResult {
        return captureInvocation(input) {
            method.invoke(null, input)?.toString().orEmpty()
        }
    }
}

private class MainExecutionPlan(
    private val method: Method,
    private val withArgs: Boolean,
) : ExecutionPlan {
    override fun execute(input: String): ExecutionResult {
        return captureInvocation(input) {
            if (withArgs) {
                method.invoke(null, arrayOf<String>())
            } else {
                method.invoke(null)
            }
            ""
        }
    }
}

private fun captureInvocation(
    input: String,
    invokeBlock: () -> String,
): ExecutionResult {
    val originalIn = System.`in`
    val originalOut = System.out
    val originalErr = System.err
    val stdoutBuffer = BoundedByteArrayOutputStream(MAX_STDOUT_BYTES)
    val stderrBuffer = BoundedByteArrayOutputStream(MAX_STDERR_BYTES)
    val stdoutStream = PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8.name())
    val stderrStream = PrintStream(stderrBuffer, true, StandardCharsets.UTF_8.name())
    try {
        System.setIn(ByteArrayInputStream(input.toByteArray(StandardCharsets.UTF_8)))
        System.setOut(stdoutStream)
        System.setErr(stderrStream)
        val returnedOutput = invokeBlock()
        stdoutBuffer.appendText(returnedOutput)
        stdoutStream.flush()
        stderrStream.flush()
        return ExecutionResult(
            output = stdoutBuffer.asUtf8String(),
            stderr = stderrBuffer.asUtf8String(),
        )
    } catch (error: Throwable) {
        stdoutStream.flush()
        stderrStream.flush()
        throw ExecutionFailureException(
            output = stdoutBuffer.asUtf8String(),
            stderr = stderrBuffer.asUtf8String(),
            cause = rootCause(error),
        )
    } finally {
        stdoutStream.close()
        stderrStream.close()
        System.setIn(originalIn)
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
}

private fun resolveExecutionPlan(classLoader: URLClassLoader): ExecutionPlan {
    val candidateClasses = buildList {
        runCatching { classLoader.loadClass("SolutionKt") }.getOrNull()?.let(::add)
        runCatching { classLoader.loadClass("Solution") }.getOrNull()?.let(::add)
    }

    candidateClasses.forEach { entryClass ->
        entryClass.methods.firstOrNull { method ->
            method.name == "solve" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == String::class.java
        }?.let { method ->
            return SolveExecutionPlan(method)
        }
    }

    candidateClasses.forEach { entryClass ->
        entryClass.methods.firstOrNull { method ->
            method.name == "main" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Array<String>::class.java
        }?.let { method ->
            return MainExecutionPlan(method = method, withArgs = true)
        }
    }

    candidateClasses.forEach { entryClass ->
        entryClass.methods.firstOrNull { method ->
            method.name == "main" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0
        }?.let { method ->
            return MainExecutionPlan(method = method, withArgs = false)
        }
    }

    throw IllegalStateException("No supported entrypoint found. Provide solve(input: String): String or main().")
}

private fun performRun(
    userClassesPath: String,
    request: WorkerRequest,
): WorkerResponse {
    val testId = request.testId ?: -1
    val input = decodeBase64(request.inputBase64.orEmpty())
    val warmupIterations = request.warmupIterations.coerceAtLeast(0)

    return try {
        URLClassLoader(arrayOf(java.io.File(userClassesPath).toURI().toURL()), object {}.javaClass.classLoader).use { classLoader ->
            val plan = resolveExecutionPlan(classLoader)
            repeat(warmupIterations) {
                plan.execute(input)
            }
            System.gc()
            System.runFinalization()
            Thread.sleep(GC_SETTLE_MILLIS)
            val measuredRuns = List(MEASURED_ITERATIONS) {
                measureExecutionSample(plan, input)
            }
            val referenceRun = measuredRuns.first()
            if (measuredRuns.any { !it.matches(referenceRun) }) {
                return WorkerResponse(
                    testId = testId,
                    status = "ERROR",
                    outputBase64 = encodeBase64(referenceRun.output),
                    stderrBase64 = encodeBase64(referenceRun.stderr),
                    executionTimeNs = medianLong(measuredRuns.map { it.executionTimeNs }),
                    memoryUsedKb = medianNullableLong(measuredRuns.map { it.memoryUsedKb }),
                    internalErrorBase64 = encodeBase64("Repeated measurement runs produced inconsistent outputs or errors."),
                )
            }

            WorkerResponse(
                testId = testId,
                status = referenceRun.status,
                outputBase64 = encodeBase64(referenceRun.output),
                stderrBase64 = encodeBase64(referenceRun.stderr),
                executionTimeNs = medianLong(measuredRuns.map { it.executionTimeNs }),
                memoryUsedKb = medianNullableLong(measuredRuns.map { it.memoryUsedKb }),
                internalErrorBase64 = referenceRun.internalError?.let(::encodeBase64),
            )
        }
    } catch (error: Throwable) {
        WorkerResponse(
            testId = testId,
            status = "ERROR",
            outputBase64 = encodeBase64(""),
            stderrBase64 = encodeBase64(""),
            executionTimeNs = 0L,
            memoryUsedKb = null,
            internalErrorBase64 = encodeBase64(throwableToString(rootCause(error))),
        )
    }
}

private fun measureExecutionSample(
    plan: ExecutionPlan,
    input: String,
): MeasuredRunSample {
    val allocatedBefore = currentAllocatedBytes()
    val startedAt = System.nanoTime()
    return try {
        val result = plan.execute(input)
        val executionTimeNs = System.nanoTime() - startedAt
        val allocatedAfter = currentAllocatedBytes()
        MeasuredRunSample(
            status = "OK",
            output = result.output,
            stderr = result.stderr,
            executionTimeNs = executionTimeNs,
            memoryUsedKb = allocatedMemoryDeltaKb(allocatedBefore, allocatedAfter),
        )
    } catch (error: ExecutionFailureException) {
        val executionTimeNs = System.nanoTime() - startedAt
        val allocatedAfter = currentAllocatedBytes()
        MeasuredRunSample(
            status = "ERROR",
            output = error.output,
            stderr = error.stderr,
            executionTimeNs = executionTimeNs,
            memoryUsedKb = allocatedMemoryDeltaKb(allocatedBefore, allocatedAfter),
            internalError = throwableToString(rootCause(error)),
        )
    }
}

private fun allocatedMemoryDeltaKb(
    allocatedBefore: Long?,
    allocatedAfter: Long?,
): Long? {
    if (
        allocatedBefore == null ||
        allocatedAfter == null ||
        allocatedAfter < allocatedBefore
    ) {
        return null
    }
    return ((allocatedAfter - allocatedBefore) + 1023L) / 1024L
}

private fun MeasuredRunSample.matches(other: MeasuredRunSample): Boolean {
    return status == other.status &&
        output == other.output &&
        stderr == other.stderr &&
        internalError == other.internalError
}

private fun medianLong(values: List<Long>): Long {
    require(values.isNotEmpty()) { "medianLong requires at least one value." }
    val sorted = values.sorted()
    return sorted[sorted.size / 2]
}

private fun medianNullableLong(values: List<Long?>): Long? {
    val presentValues = values.filterNotNull()
    if (presentValues.size != values.size || presentValues.isEmpty()) {
        return null
    }
    return medianLong(presentValues)
}

fun main(args: Array<String>) {
    val userClassesPath = args.firstOrNull()
        ?: error("WorkerHarness requires the user classes directory as the first argument.")
    val protocolOut = PrintWriter(System.out, true)
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))

    while (true) {
        val rawLine = reader.readLine() ?: break
        val request = try {
            parseRequest(rawLine)
        } catch (error: Throwable) {
            protocolOut.println(
                responseToJson(
                    WorkerResponse(
                        testId = -1,
                        status = "ERROR",
                        outputBase64 = encodeBase64(""),
                        stderrBase64 = encodeBase64(""),
                        internalErrorBase64 = encodeBase64(throwableToString(rootCause(error))),
                    )
                )
            )
            protocolOut.flush()
            continue
        }

        when (request.type) {
            "RUN_TEST" -> {
                protocolOut.println(responseToJson(performRun(userClassesPath, request)))
                protocolOut.flush()
            }
            "SHUTDOWN" -> {
                return
            }
        }
    }
}
"""
