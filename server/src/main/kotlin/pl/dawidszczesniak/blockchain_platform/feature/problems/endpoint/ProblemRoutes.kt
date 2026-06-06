package pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthCsrfException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireAuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireTrustedOrigin
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.AuthSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.CompetitionLifecycleController
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemCreationController
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemParticipationController
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemQueryController
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemSubmissionController
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CancelCreateProblemValidationRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCompetitionLifecycleActionRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmSubmissionResultRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionLifecycleValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationCancelledException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmissionJudgeJobValidationException

internal fun Route.problemRoutes() {
    val queryController by inject<ProblemQueryController>()
    val creationController by inject<ProblemCreationController>()
    val participationController by inject<ProblemParticipationController>()
    val lifecycleController by inject<CompetitionLifecycleController>()
    val submissionController by inject<ProblemSubmissionController>()
    val authConfig by inject<AuthConfig>()
    val sessionStore by inject<AuthSessionStore>()

    get("/problems") {
        call.respond(io { queryController.getProblemSummaries() })
    }

    get("/problems/created") {
        call.handleProblemRoute {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore, checkTrustedOrigin = false)
            call.respond(io { queryController.getCreatedProblems(session.userId) })
        }
    }

    get("/problems/participation") {
        call.handleProblemRoute {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore, checkTrustedOrigin = false)
            call.respond(io { queryController.getParticipationProblems(session.userId) })
        }
    }

    get("/problems/{problemId}") {
        val problemId = call.parameters["problemId"]?.toIntOrNull()
        if (problemId == null) {
            call.respondMessage(HttpStatusCode.BadRequest, "Invalid problem identifier.")
            return@get
        }
        try {
            call.respond(io { queryController.getProblemSummary(problemId) })
        } catch (error: IllegalArgumentException) {
            call.respondMessage(HttpStatusCode.NotFound, error.message ?: "Problem not found.")
        }
    }

    post("/problems") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.CreateProblem, "Invalid create problem payload.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val request = call.receive<CreateProblemRequestDto>()
            val created = io { creationController.createProblem(session.userId, request) }
            call.respond(HttpStatusCode.Created, created)
        }
    }

    post("/problems/create/prepare") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.CreateProblem, "Invalid create problem preparation payload.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val request = call.receive<CreateProblemRequestDto>()
            val prepared = io {
                creationController.prepareCreateProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        }
    }

    post("/problems/create/confirm") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.CreateProblem, "Invalid create problem confirmation payload.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val request = call.receive<ConfirmCreateProblemRequestDto>()
            val created = io {
                creationController.confirmCreateProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.Created, created)
        }
    }

    post("/problems/create/validate") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.CreateProblemValidation, "Invalid create problem validation payload.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val request = call.receive<ValidateCreateProblemRequestDto>()
            val result = io { creationController.validateCreateProblem(session.userId, request) }
            call.respond(HttpStatusCode.OK, result)
        }
    }

    post("/problems/create/validate/cancel") {
        call.handleProblemRoute {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val request = call.receive<CancelCreateProblemValidationRequestDto>()
            io { creationController.cancelCreateProblemValidation(session.userId, request) }
            call.respond(HttpStatusCode.Accepted, mapOf("cancelled" to true))
        }
    }

    post("/problems/{problemId}/join") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.JoinProblem, "Cannot register for this problem.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                JoinProblemValidationException("Invalid problem identifier.")
            }
            val joined = io {
                participationController.joinProblem(
                    userId = session.userId,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, joined)
        }
    }

    post("/problems/{problemId}/join/prepare") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.JoinProblem, "Cannot prepare join transaction for this problem.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                JoinProblemValidationException("Invalid problem identifier.")
            }
            val prepared = io {
                participationController.prepareJoinProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        }
    }

    post("/problems/{problemId}/join/confirm") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.JoinProblem, "Cannot confirm join transaction for this problem.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                JoinProblemValidationException("Invalid problem identifier.")
            }
            val request = call.receive<ConfirmJoinProblemRequestDto>()
            val joined = io {
                participationController.confirmJoinProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, joined)
        }
    }

    post("/problems/{problemId}/settle/prepare") {
        call.handleProblemRoute(
            ProblemValidationError(
                ProblemValidationErrorKind.CompetitionLifecycle,
                "Cannot prepare settlement transaction for this competition.",
            )
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                CompetitionLifecycleValidationException("Invalid problem identifier.")
            }
            val prepared = io {
                lifecycleController.prepareSettleCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        }
    }

    post("/problems/{problemId}/settle/confirm") {
        call.handleProblemRoute(
            ProblemValidationError(
                ProblemValidationErrorKind.CompetitionLifecycle,
                "Cannot confirm settlement transaction for this competition.",
            )
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                CompetitionLifecycleValidationException("Invalid problem identifier.")
            }
            val request = call.receive<ConfirmCompetitionLifecycleActionRequestDto>()
            val confirmed = io {
                lifecycleController.confirmSettleCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, confirmed)
        }
    }

    post("/problems/{problemId}/cancel/prepare") {
        call.handleProblemRoute(
            ProblemValidationError(
                ProblemValidationErrorKind.CompetitionLifecycle,
                "Cannot prepare cancellation transaction for this competition.",
            )
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                CompetitionLifecycleValidationException("Invalid problem identifier.")
            }
            val prepared = io {
                lifecycleController.prepareCancelCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        }
    }

    post("/problems/{problemId}/cancel/confirm") {
        call.handleProblemRoute(
            ProblemValidationError(
                ProblemValidationErrorKind.CompetitionLifecycle,
                "Cannot confirm cancellation transaction for this competition.",
            )
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                CompetitionLifecycleValidationException("Invalid problem identifier.")
            }
            val request = call.receive<ConfirmCompetitionLifecycleActionRequestDto>()
            val confirmed = io {
                lifecycleController.confirmCancelCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, confirmed)
        }
    }

    post("/problems/{problemId}/run") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.RunProblem, "Cannot run this solution.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                RunProblemValidationException("Invalid problem identifier.")
            }
            val request = call.receive<RunProblemRequestDto>()
            val result = io {
                submissionController.runProblemCode(
                    userId = session.userId,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }
    }

    post("/problems/{problemId}/submit") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.SubmissionJob, "Cannot submit this solution.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val problemId = call.requireIntParameter("problemId") {
                SubmissionJudgeJobValidationException("Invalid problem identifier.")
            }
            val request = call.receive<RunProblemRequestDto>()
            val result = io {
                submissionController.submitProblemCode(
                    userId = session.userId,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.Accepted, result)
        }
    }

    post("/problems/submissions/{submissionId}/confirm") {
        call.handleProblemRoute(
            ProblemValidationError(ProblemValidationErrorKind.SubmissionJob, "Cannot confirm this submission transaction.")
        ) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val submissionId = call.requireLongParameter("submissionId") {
                SubmissionJudgeJobValidationException("Invalid submission identifier.")
            }
            val request = call.receive<ConfirmSubmissionResultRequestDto>()
            val result = io {
                submissionController.confirmSubmissionResultOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    submissionId = submissionId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }
    }

    get("/problems/submission-jobs/{jobId}") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.SubmissionJob, "Cannot load this submission judge job.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val jobId = call.requireLongParameter("jobId") {
                SubmissionJudgeJobValidationException("Invalid submission judge job identifier.")
            }
            val result = io {
                submissionController.getSubmissionJudgeJob(
                    userId = session.userId,
                    jobId = jobId,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        }
    }

    post("/problems/submission-jobs/{jobId}/retry") {
        call.handleProblemRoute(ProblemValidationError(ProblemValidationErrorKind.SubmissionJob, "Cannot retry this submission judge job.")) {
            val session = call.requireAuthenticatedSession(authConfig, sessionStore)
            val jobId = call.requireLongParameter("jobId") {
                SubmissionJudgeJobValidationException("Invalid submission judge job identifier.")
            }
            val result = io {
                submissionController.retrySubmissionJudgeJob(
                    userId = session.userId,
                    jobId = jobId,
                )
            }
            call.respond(HttpStatusCode.Accepted, result)
        }
    }
}

private suspend fun ApplicationCall.handleProblemRoute(
    validationError: ProblemValidationError? = null,
    action: suspend () -> Unit,
) {
    try {
        action()
    } catch (error: Throwable) {
        if (!respondProblemRouteError(error, validationError)) {
            throw error
        }
    }
}

private fun ApplicationCall.requireAuthenticatedSession(
    authConfig: AuthConfig,
    sessionStore: AuthSessionStore,
    checkTrustedOrigin: Boolean = true,
): AuthSession {
    if (checkTrustedOrigin) {
        requireTrustedOrigin(authConfig)
    }
    return requireAuthSession(authConfig, sessionStore)
}

private suspend fun ApplicationCall.respondProblemRouteError(
    error: Throwable,
    validationError: ProblemValidationError?,
): Boolean {
    when (error) {
        is AuthRequiredException -> {
            respondMessage(HttpStatusCode.Unauthorized, error.message ?: "Login required.")
            return true
        }

        is AuthCsrfException -> {
            respondMessage(HttpStatusCode.Forbidden, error.message ?: "Request origin is not allowed.")
            return true
        }

        is AuthServiceUnavailableException -> {
            respondMessage(HttpStatusCode.ServiceUnavailable, error.message ?: "Authentication service is unavailable.")
            return true
        }
    }

    if (validationError == null || !validationError.matches(error)) {
        return false
    }

    val status = when (error) {
        is CreateProblemValidationCancelledException -> HttpStatusCode.Conflict
        else -> HttpStatusCode.BadRequest
    }
    val fallback = when (error) {
        is CreateProblemValidationCancelledException -> "Create problem validation was cancelled."
        else -> validationError.fallbackMessage
    }
    respondMessage(status, error.message ?: fallback)
    return true
}

private data class ProblemValidationError(
    val kind: ProblemValidationErrorKind,
    val fallbackMessage: String,
) {
    fun matches(error: Throwable): Boolean {
        return when (kind) {
            ProblemValidationErrorKind.CreateProblem -> error is CreateProblemValidationException
            ProblemValidationErrorKind.CreateProblemValidation ->
                error is CreateProblemValidationException || error is CreateProblemValidationCancelledException
            ProblemValidationErrorKind.JoinProblem -> error is JoinProblemValidationException
            ProblemValidationErrorKind.CompetitionLifecycle -> error is CompetitionLifecycleValidationException
            ProblemValidationErrorKind.RunProblem -> error is RunProblemValidationException
            ProblemValidationErrorKind.SubmissionJob -> error is SubmissionJudgeJobValidationException
        }
    }
}

private enum class ProblemValidationErrorKind {
    CreateProblem,
    CreateProblemValidation,
    JoinProblem,
    CompetitionLifecycle,
    RunProblem,
    SubmissionJob,
}

private fun ApplicationCall.requireIntParameter(name: String, error: () -> Throwable): Int {
    return parameters[name]?.toIntOrNull() ?: throw error()
}

private fun ApplicationCall.requireLongParameter(name: String, error: () -> Throwable): Long {
    return parameters[name]?.toLongOrNull() ?: throw error()
}

private suspend fun ApplicationCall.respondMessage(status: HttpStatusCode, message: String) {
    respond(status, mapOf("message" to message))
}

private suspend fun <T> io(block: () -> T): T {
    return withContext(Dispatchers.IO) { block() }
}
