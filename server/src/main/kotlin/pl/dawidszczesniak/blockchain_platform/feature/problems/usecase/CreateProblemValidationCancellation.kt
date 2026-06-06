package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class CreateProblemValidationCancelledException : RuntimeException("Create problem validation was cancelled.")

internal interface SandboxRunCancellation {
    val isCancelled: Boolean
    fun throwIfCancelled()
    fun registerCancellationAction(action: () -> Unit)
}

internal interface CreateProblemValidationCancellationRegistry {
    fun open(userId: Long, runId: String): SandboxRunCancellation
    fun finish(userId: Long, runId: String)
    fun cancel(userId: Long, runId: String)
}

internal class CreateProblemValidationCancellationRegistryImpl : CreateProblemValidationCancellationRegistry {
    private val runs = ConcurrentHashMap<ValidationRunKey, ManagedValidationRun>()

    override fun open(userId: Long, runId: String): SandboxRunCancellation {
        val key = ValidationRunKey(userId = userId, runId = runId)
        val run = ManagedValidationRun()
        runs[key] = run
        return run
    }

    override fun finish(userId: Long, runId: String) {
        runs.remove(ValidationRunKey(userId = userId, runId = runId))
    }

    override fun cancel(userId: Long, runId: String) {
        runs.remove(ValidationRunKey(userId = userId, runId = runId))?.cancel()
    }
}

private data class ValidationRunKey(
    val userId: Long,
    val runId: String,
)

private class ManagedValidationRun : SandboxRunCancellation {
    private val cancelled = AtomicBoolean(false)
    private val cancelActions = CopyOnWriteArrayList<() -> Unit>()

    override val isCancelled: Boolean
        get() = cancelled.get()

    override fun throwIfCancelled() {
        if (cancelled.get()) {
            throw CreateProblemValidationCancelledException()
        }
    }

    override fun registerCancellationAction(action: () -> Unit) {
        if (cancelled.get()) {
            action()
            return
        }
        cancelActions += action
        if (cancelled.get() && cancelActions.remove(action)) {
            action()
        }
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return
        }
        cancelActions.forEach { action ->
            runCatching { action() }
        }
        cancelActions.clear()
    }
}

internal interface CancelCreateProblemValidationUseCase {
    operator fun invoke(userId: Long, runId: String)
}

internal class CancelCreateProblemValidationUseCaseImpl(
    private val cancellationRegistry: CreateProblemValidationCancellationRegistry,
) : CancelCreateProblemValidationUseCase {
    override fun invoke(userId: Long, runId: String) {
        cancellationRegistry.cancel(userId = userId, runId = runId)
    }
}
