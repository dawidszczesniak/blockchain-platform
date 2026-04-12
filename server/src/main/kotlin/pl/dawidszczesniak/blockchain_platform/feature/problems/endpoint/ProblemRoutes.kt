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
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationException
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
}
