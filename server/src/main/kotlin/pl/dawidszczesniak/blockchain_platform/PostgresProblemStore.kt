package pl.dawidszczesniak.blockchain_platform

import java.sql.DriverManager
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary

internal class PostgresProblemStore(
    private val config: PostgresConfig = PostgresConfig.fromEnvironment(),
) {
    private val database: Database

    init {
        Class.forName("org.postgresql.Driver")
        database = Database.connect(
            url = config.jdbcUrl,
            user = config.user,
            password = config.password,
        )
    }

    fun initialize() {
        applySchema()
        seedProblemsIfEmpty()
    }

    fun fetchProblemSummaries(): List<ProblemSummary> {
        return transaction(database) {
            val participantCounts = participantCountsByProblem()
            val today = LocalDate.now()

            ProblemsTable
                .selectAll()
                .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
                    val problemId = row[ProblemsTable.problemId]
                    val daysToJoinEnd = daysBetween(today, row[ProblemsTable.joinUntilDate]).coerceAtLeast(0)
                    ProblemSummary(
                        id = problemId.toInt(),
                        title = row[ProblemsTable.title],
                        description = row[ProblemsTable.description],
                        prizeAmount = row[ProblemsTable.prizeAmount],
                        entryFeeAmount = row[ProblemsTable.entryFeeAmount],
                        requiredParticipants = row[ProblemsTable.requiredParticipants],
                        registeredParticipants = participantCounts[problemId] ?: 0,
                        daysToStart = daysToJoinEnd,
                        daysToJoinEnd = daysToJoinEnd,
                        joinUntilLabel = row[ProblemsTable.joinUntilDate].toString(),
                        submitUntilLabel = row[ProblemsTable.submitUntilDate].toString(),
                    )
                }
        }
    }

    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem> {
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            val participantCounts = participantCountsByProblem()
            val submissionCounts = submissionCountsByProblem()
            val winnerByProblem = winnerInfoByProblem()
            val today = LocalDate.now()

            ProblemsTable
                .selectAll()
                .where { ProblemsTable.createdByUserId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
                    val problemId = row[ProblemsTable.problemId]
                    val daysToJoinEnd = daysBetween(today, row[ProblemsTable.joinUntilDate])
                    val daysToSubmitEnd = daysBetween(today, row[ProblemsTable.submitUntilDate])
                    val winnerInfo = winnerByProblem[problemId]
                    val status = createdProblemStatus(
                        problemStatus = row[ProblemsTable.problemStatus],
                        daysToJoinEnd = daysToJoinEnd,
                        daysToSubmitEnd = daysToSubmitEnd,
                        winnerWallet = winnerInfo?.walletAddress,
                    )
                    CreatedProblem(
                        id = problemId.toInt(),
                        title = row[ProblemsTable.title],
                        status = status,
                        requiredParticipants = row[ProblemsTable.requiredParticipants],
                        registeredParticipants = participantCounts[problemId] ?: 0,
                        submissions = submissionCounts[problemId] ?: 0,
                        startedOn = if (status == CreatedProblemStatus.Started) {
                            row[ProblemsTable.joinUntilDate].toString()
                        } else {
                            null
                        },
                        finishedOn = if (status == CreatedProblemStatus.Completed) {
                            winnerInfo?.wonAtLabel ?: row[ProblemsTable.submitUntilDate].toString()
                        } else {
                            null
                        },
                        registrationEnds = if (status == CreatedProblemStatus.Waiting) {
                            row[ProblemsTable.joinUntilDate].toString()
                        } else {
                            null
                        },
                        timeElapsed = if (status == CreatedProblemStatus.Expired) {
                            row[ProblemsTable.submitUntilDate].toString()
                        } else {
                            null
                        },
                        winner = if (status == CreatedProblemStatus.Completed) {
                            winnerInfo?.walletAddress
                        } else {
                            null
                        },
                    )
                }
        }
    }

    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem> {
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            val participantCounts = participantCountsByProblem()
            val attemptCounts = submissionAttemptsByProblemAndUser()
            val today = LocalDate.now()

            (ProblemParticipantsTable innerJoin ProblemsTable)
                .selectAll()
                .where { ProblemParticipantsTable.userId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
                    val problemId = row[ProblemsTable.problemId]
                    val attempts = attemptCounts[problemId to userId] ?: 0
                    val daysToSubmitEnd = daysBetween(today, row[ProblemsTable.submitUntilDate])
                    ParticipationProblem(
                        id = problemId.toInt(),
                        title = row[ProblemsTable.title],
                        status = if (attempts > 0) {
                            ParticipationStatus.Submitted
                        } else {
                            ParticipationStatus.NotSubmitted
                        },
                        timeLeftLabel = "${max(0, daysToSubmitEnd)}d",
                        participants = participantCounts[problemId] ?: 0,
                        attemptsCount = attempts,
                    )
                }
        }
    }

    private fun applySchema() {
        val schemaSql = javaClass.classLoader.getResource(SCHEMA_RESOURCE_PATH)?.readText()
            ?: error("Missing SQL resource '$SCHEMA_RESOURCE_PATH'.")
        DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(schemaSql)
            }
        }
    }

    private fun seedProblemsIfEmpty() {
        transaction(database) {
            val existingCount = ProblemsTable.selectAll().count()
            if (existingCount > 0L) {
                return@transaction
            }

            val userIds = (1..11).map { index ->
                insertUser(walletAddressForSeed(index))
            }

            val firstProblemId = insertProblem(
                title = "Neural Network Compression",
                description = "Optimize a neural model pipeline to reduce inference cost.",
                problemStatus = ProblemLifecycleStatus.Open,
                prizeAmount = 25,
                entryFeeAmount = 3,
                requiredParticipants = 12,
                joinUntilDate = LocalDate.parse("2026-03-08"),
                submitUntilDate = LocalDate.parse("2026-03-15"),
                createdByUserId = userIds[0],
            )

            val secondProblemId = insertProblem(
                title = "Oracle Aggregation",
                description = "Build robust oracle aggregation logic for volatile market feeds.",
                problemStatus = ProblemLifecycleStatus.Closed,
                prizeAmount = 40,
                entryFeeAmount = 5,
                requiredParticipants = 18,
                joinUntilDate = LocalDate.parse("2026-03-10"),
                submitUntilDate = LocalDate.parse("2026-03-17"),
                createdByUserId = userIds[1],
            )

            userIds.take(7).forEach { userId ->
                registerUser(problemId = firstProblemId, userId = userId)
            }
            userIds.forEach { userId ->
                registerUser(problemId = secondProblemId, userId = userId)
            }

            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[0],
                status = SubmissionAttemptStatus.Rejected,
            )
            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[0],
                status = SubmissionAttemptStatus.Accepted,
            )
            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[2],
                status = SubmissionAttemptStatus.Accepted,
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Error,
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Rejected,
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Accepted,
            )

            insertProblemWinner(
                problemId = firstProblemId,
                winnerUserId = userIds[2],
                payoutAmount = 25,
            )
            insertProblemWinner(
                problemId = secondProblemId,
                winnerUserId = userIds[4],
                payoutAmount = 40,
            )
        }
    }

    private fun resolveDefaultUserId(): Long? {
        return UsersTable
            .selectAll()
            .orderBy(UsersTable.userId to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.get(UsersTable.userId)
    }

    private fun participantCountsByProblem(): Map<Long, Int> {
        val countExpression = ProblemParticipantsTable.userId.count()
        return ProblemParticipantsTable
            .select(ProblemParticipantsTable.problemId, countExpression)
            .groupBy(ProblemParticipantsTable.problemId)
            .associate { row ->
                row[ProblemParticipantsTable.problemId] to row[countExpression].toInt()
            }
    }

    private fun submissionCountsByProblem(): Map<Long, Int> {
        val countExpression = ProblemSubmissionsTable.submissionId.count()
        return ProblemSubmissionsTable
            .select(ProblemSubmissionsTable.problemId, countExpression)
            .groupBy(ProblemSubmissionsTable.problemId)
            .associate { row ->
                row[ProblemSubmissionsTable.problemId] to row[countExpression].toInt()
            }
    }

    private fun submissionAttemptsByProblemAndUser(): Map<Pair<Long, Long>, Int> {
        val countExpression = ProblemSubmissionsTable.submissionId.count()
        return ProblemSubmissionsTable
            .select(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId, countExpression)
            .groupBy(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId)
            .associate { row ->
                (row[ProblemSubmissionsTable.problemId] to row[ProblemSubmissionsTable.userId]) to
                    row[countExpression].toInt()
            }
    }

    private fun winnerInfoByProblem(): Map<Long, WinnerInfo> {
        val winnersWithUsers = ProblemWinnersTable.innerJoin(
            UsersTable,
            { ProblemWinnersTable.winnerUserId },
            { UsersTable.userId },
        )

        val rows = winnersWithUsers
            .selectAll()
            .orderBy(
                ProblemWinnersTable.problemId to SortOrder.ASC,
                ProblemWinnersTable.wonAt to SortOrder.DESC,
                ProblemWinnersTable.winnerUserId to SortOrder.DESC,
            )

        val result = mutableMapOf<Long, WinnerInfo>()
        rows.forEach { row ->
            val problemId = row[ProblemWinnersTable.problemId]
            if (problemId !in result) {
                val wonAtLabel = row[ProblemWinnersTable.wonAt].toLocalDate().toString()
                result[problemId] = WinnerInfo(
                    walletAddress = row[UsersTable.walletAddress],
                    wonAtLabel = wonAtLabel,
                )
            }
        }
        return result
    }

    private fun createdProblemStatus(
        problemStatus: String,
        daysToJoinEnd: Int,
        daysToSubmitEnd: Int,
        winnerWallet: String?,
    ): CreatedProblemStatus {
        if (problemStatus == ProblemLifecycleStatus.Closed.dbValue) {
            return if (!winnerWallet.isNullOrBlank()) {
                CreatedProblemStatus.Completed
            } else {
                CreatedProblemStatus.Expired
            }
        }
        if (!winnerWallet.isNullOrBlank()) {
            return CreatedProblemStatus.Completed
        }
        if (daysToJoinEnd >= 0) {
            return CreatedProblemStatus.Waiting
        }
        if (daysToSubmitEnd >= 0) {
            return CreatedProblemStatus.Started
        }
        return CreatedProblemStatus.Expired
    }

    private fun insertProblem(
        title: String,
        description: String,
        problemStatus: ProblemLifecycleStatus,
        prizeAmount: Int,
        entryFeeAmount: Int,
        requiredParticipants: Int,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
        createdByUserId: Long,
    ): Long {
        val inserted = ProblemsTable.insert {
            it[ProblemsTable.createdByUserId] = createdByUserId
            it[ProblemsTable.problemStatus] = problemStatus.dbValue
            it[ProblemsTable.title] = title
            it[ProblemsTable.description] = description
            it[ProblemsTable.prizeAmount] = prizeAmount
            it[ProblemsTable.entryFeeAmount] = entryFeeAmount
            it[ProblemsTable.requiredParticipants] = requiredParticipants
            it[ProblemsTable.joinUntilDate] = joinUntilDate
            it[ProblemsTable.submitUntilDate] = submitUntilDate
        }
        return inserted[ProblemsTable.problemId]
    }

    private fun insertUser(walletAddress: String): Long {
        val inserted = UsersTable.insert {
            it[UsersTable.walletAddress] = walletAddress
        }
        return inserted[UsersTable.userId]
    }

    private fun registerUser(problemId: Long, userId: Long) {
        ProblemParticipantsTable.insert {
            it[ProblemParticipantsTable.problemId] = problemId
            it[ProblemParticipantsTable.userId] = userId
        }
    }

    private fun insertProblemSubmission(
        problemId: Long,
        userId: Long,
        status: SubmissionAttemptStatus,
    ) {
        ProblemSubmissionsTable.insert {
            it[ProblemSubmissionsTable.problemId] = problemId
            it[ProblemSubmissionsTable.userId] = userId
            it[ProblemSubmissionsTable.status] = status.dbValue
        }
    }

    private fun insertProblemWinner(
        problemId: Long,
        winnerUserId: Long,
        payoutAmount: Int,
    ) {
        ProblemWinnersTable.insert {
            it[ProblemWinnersTable.problemId] = problemId
            it[ProblemWinnersTable.winnerUserId] = winnerUserId
            it[ProblemWinnersTable.payoutAmount] = payoutAmount
        }
    }

    private fun walletAddressForSeed(index: Int): String {
        return "0x${index.toString(16).padStart(40, '0')}"
    }

    private fun daysBetween(fromDate: LocalDate, toDate: LocalDate): Int {
        return ChronoUnit.DAYS.between(fromDate, toDate).toInt()
    }
}

private data class WinnerInfo(
    val walletAddress: String,
    val wonAtLabel: String,
)

private enum class ProblemLifecycleStatus(val dbValue: String) {
    Open("open"),
    Closed("closed"),
}

private enum class SubmissionAttemptStatus(val dbValue: String) {
    Accepted("accepted"),
    Rejected("rejected"),
    Error("error"),
}

internal data class PostgresConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): PostgresConfig {
            val host = env["DB_HOST"]?.takeIf { it.isNotBlank() } ?: "localhost"
            val port = env["DB_PORT"]?.takeIf { it.isNotBlank() } ?: "5432"
            val name = env["DB_NAME"]?.takeIf { it.isNotBlank() } ?: "blockchain_platform"
            val user = env["DB_USER"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_USER"]?.takeIf { it.isNotBlank() }
                ?: "blockchain_user"
            val password = env["DB_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: "blockchain_pass"
            val jdbcUrl = env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
                ?: "jdbc:postgresql://$host:$port/$name"

            return PostgresConfig(
                jdbcUrl = jdbcUrl,
                user = user,
                password = password,
            )
        }
    }
}

private object UsersTable : Table("users") {
    val userId = long("user_id").autoIncrement()
    val walletAddress = varchar("wallet_address", length = 66)
    val registeredAt = datetime("registered_at")
    val lastLoginAt = datetime("last_login_at")

    override val primaryKey = PrimaryKey(userId)
}

private object ProblemsTable : Table("problems") {
    val problemId = long("problem_id").autoIncrement()
    val createdByUserId = long("created_by_user_id")
    val problemStatus = varchar("problem_status", length = 16)
    val title = text("title")
    val description = text("description")
    val prizeAmount = integer("prize_amount")
    val entryFeeAmount = integer("entry_fee_amount")
    val requiredParticipants = integer("required_participants")
    val joinUntilDate = date("join_until_date")
    val submitUntilDate = date("submit_until_date")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(problemId)
}

private object ProblemParticipantsTable : Table("problem_participants") {
    val problemId = long("problem_id")
    val userId = long("user_id")
    val registeredAt = datetime("registered_at")

    override val primaryKey = PrimaryKey(problemId, userId)
}

private object ProblemSubmissionsTable : Table("problem_submissions") {
    val submissionId = long("submission_id").autoIncrement()
    val problemId = long("problem_id")
    val userId = long("user_id")
    val status = varchar("status", length = 16)
    val submittedAt = datetime("submitted_at")

    override val primaryKey = PrimaryKey(submissionId)
}

private object ProblemWinnersTable : Table("problem_winners") {
    val problemId = long("problem_id")
    val winnerUserId = long("winner_user_id")
    val payoutAmount = integer("payout_amount")
    val wonAt = datetime("won_at")

    override val primaryKey = PrimaryKey(problemId, winnerUserId)
}

private const val SCHEMA_RESOURCE_PATH = "db/schema.sql"
