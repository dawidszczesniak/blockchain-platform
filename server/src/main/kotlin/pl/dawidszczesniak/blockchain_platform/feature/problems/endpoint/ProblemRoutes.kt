package pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint

import io.ktor.http.HttpStatusCode
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
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireAuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireTrustedOrigin
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.AuthSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CancelCreateProblemValidationRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCompetitionLifecycleActionRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmSubmissionResultRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationCancelledException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionLifecycleValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemValidationException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmissionJudgeJobValidationException

internal fun Route.problemRoutes() {
    val controller by inject<ProblemController>()
    val authConfig by inject<AuthConfig>()
    val sessionStore by inject<AuthSessionStore>()

    get("/problems") {
        val problems = withContext(Dispatchers.IO) {
            controller.getProblemSummaries()
        }
        call.respond(problems)
    }
    get("/problems/created") {
        try {
            val session = call.requireAuthSession(authConfig, sessionStore)
            val createdProblems = withContext(Dispatchers.IO) {
                controller.getCreatedProblems(session.userId)
            }
            call.respond(createdProblems)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    get("/problems/participation") {
        try {
            val session = call.requireAuthSession(authConfig, sessionStore)
            val participationProblems = withContext(Dispatchers.IO) {
                controller.getParticipationProblems(session.userId)
            }
            call.respond(participationProblems)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    get("/problems/{problemId}") {
        val problemId = call.parameters["problemId"]?.toIntOrNull()
        if (problemId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to "Invalid problem identifier."),
            )
            return@get
        }
        try {
            val problem = withContext(Dispatchers.IO) {
                controller.getProblemSummary(problemId)
            }
            call.respond(problem)
        } catch (error: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("message" to (error.message ?: "Problem not found.")),
            )
        }
    }
    post("/problems") {
        val request = call.receive<CreateProblemRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val created = withContext(Dispatchers.IO) {
                controller.createProblem(session.userId, request)
            }
            call.respond(HttpStatusCode.Created, created)
        } catch (error: CreateProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid create problem payload.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/create/prepare") {
        val request = call.receive<CreateProblemRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val prepared = withContext(Dispatchers.IO) {
                controller.prepareCreateProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        } catch (error: CreateProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid create problem preparation payload.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/create/confirm") {
        val request = call.receive<ConfirmCreateProblemRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val created = withContext(Dispatchers.IO) {
                controller.confirmCreateProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.Created, created)
        } catch (error: CreateProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid create problem confirmation payload.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/create/validate") {
        val request = call.receive<ValidateCreateProblemRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val result = withContext(Dispatchers.IO) {
                controller.validateCreateProblem(session.userId, request)
            }
            call.respond(HttpStatusCode.OK, result)
        } catch (error: CreateProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid create problem validation payload.")),
            )
        } catch (error: CreateProblemValidationCancelledException) {
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("message" to (error.message ?: "Create problem validation was cancelled.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/create/validate/cancel") {
        val request = call.receive<CancelCreateProblemValidationRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            withContext(Dispatchers.IO) {
                controller.cancelCreateProblemValidation(session.userId, request)
            }
            call.respond(HttpStatusCode.Accepted, mapOf("cancelled" to true))
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/join") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw JoinProblemValidationException("Invalid problem identifier.")
            val joined = withContext(Dispatchers.IO) {
                controller.joinProblem(
                    userId = session.userId,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, joined)
        } catch (error: JoinProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot register for this problem.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/join/prepare") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw JoinProblemValidationException("Invalid problem identifier.")
            val prepared = withContext(Dispatchers.IO) {
                controller.prepareJoinProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        } catch (error: JoinProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot prepare join transaction for this problem.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/join/confirm") {
        val request = call.receive<ConfirmJoinProblemRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw JoinProblemValidationException("Invalid problem identifier.")
            val joined = withContext(Dispatchers.IO) {
                controller.confirmJoinProblemOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, joined)
        } catch (error: JoinProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot confirm join transaction for this problem.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/settle/prepare") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw CompetitionLifecycleValidationException("Invalid problem identifier.")
            val prepared = withContext(Dispatchers.IO) {
                controller.prepareSettleCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        } catch (error: CompetitionLifecycleValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot prepare settlement transaction for this competition.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/settle/confirm") {
        val request = call.receive<ConfirmCompetitionLifecycleActionRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw CompetitionLifecycleValidationException("Invalid problem identifier.")
            val confirmed = withContext(Dispatchers.IO) {
                controller.confirmSettleCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, confirmed)
        } catch (error: CompetitionLifecycleValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot confirm settlement transaction for this competition.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/cancel/prepare") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw CompetitionLifecycleValidationException("Invalid problem identifier.")
            val prepared = withContext(Dispatchers.IO) {
                controller.prepareCancelCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                )
            }
            call.respond(HttpStatusCode.OK, prepared)
        } catch (error: CompetitionLifecycleValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot prepare cancellation transaction for this competition.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/cancel/confirm") {
        val request = call.receive<ConfirmCompetitionLifecycleActionRequestDto>()
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw CompetitionLifecycleValidationException("Invalid problem identifier.")
            val confirmed = withContext(Dispatchers.IO) {
                controller.confirmCancelCompetitionOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, confirmed)
        } catch (error: CompetitionLifecycleValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot confirm cancellation transaction for this competition.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/run") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw RunProblemValidationException("Invalid problem identifier.")
            val request = call.receive<RunProblemRequestDto>()
            val result = withContext(Dispatchers.IO) {
                controller.runProblemCode(
                    userId = session.userId,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        } catch (error: RunProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot run this solution.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/{problemId}/submit") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val problemId = call.parameters["problemId"]?.toIntOrNull()
                ?: throw SubmissionJudgeJobValidationException("Invalid problem identifier.")
            val request = call.receive<RunProblemRequestDto>()
            val result = withContext(Dispatchers.IO) {
                controller.submitProblemCode(
                    userId = session.userId,
                    problemId = problemId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.Accepted, result)
        } catch (error: SubmissionJudgeJobValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot submit this solution.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/submissions/{submissionId}/confirm") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val submissionId = call.parameters["submissionId"]?.toLongOrNull()
                ?: throw SubmissionJudgeJobValidationException("Invalid submission identifier.")
            val request = call.receive<ConfirmSubmissionResultRequestDto>()
            val result = withContext(Dispatchers.IO) {
                controller.confirmSubmissionResultOnChain(
                    userId = session.userId,
                    walletAddress = session.walletAddress,
                    submissionId = submissionId,
                    request = request,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        } catch (error: SubmissionJudgeJobValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot confirm this submission transaction.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    get("/problems/submission-jobs/{jobId}") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val jobId = call.parameters["jobId"]?.toLongOrNull()
                ?: throw SubmissionJudgeJobValidationException("Invalid submission judge job identifier.")
            val result = withContext(Dispatchers.IO) {
                controller.getSubmissionJudgeJob(
                    userId = session.userId,
                    jobId = jobId,
                )
            }
            call.respond(HttpStatusCode.OK, result)
        } catch (error: SubmissionJudgeJobValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot load this submission judge job.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
    post("/problems/submission-jobs/{jobId}/retry") {
        try {
            call.requireTrustedOrigin(authConfig)
            val session = call.requireAuthSession(authConfig, sessionStore)
            val jobId = call.parameters["jobId"]?.toLongOrNull()
                ?: throw SubmissionJudgeJobValidationException("Invalid submission judge job identifier.")
            val result = withContext(Dispatchers.IO) {
                controller.retrySubmissionJudgeJob(
                    userId = session.userId,
                    jobId = jobId,
                )
            }
            call.respond(HttpStatusCode.Accepted, result)
        } catch (error: SubmissionJudgeJobValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Cannot retry this submission judge job.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }
}
