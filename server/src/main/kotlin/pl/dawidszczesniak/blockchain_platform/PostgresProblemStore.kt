package pl.dawidszczesniak.blockchain_platform

import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.math.max
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary

internal class PostgresProblemStore(
    private val config: PostgresConfig = PostgresConfig.fromEnvironment(),
) {
    init {
        Class.forName("org.postgresql.Driver")
    }

    fun initialize() {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                applySchema(connection)
                seedProblemsIfEmpty(connection)
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun fetchProblemSummaries(): List<ProblemSummary> {
        return withConnection { connection ->
            connection.prepareStatement(FETCH_PROBLEMS_SQL).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                ProblemSummary(
                                    id = resultSet.getLong("id").toInt(),
                                    title = resultSet.getString("title"),
                                    description = resultSet.getString("description"),
                                    prizeAmount = resultSet.getInt("prize_amount"),
                                    entryFeeAmount = resultSet.getInt("entry_fee_amount"),
                                    requiredParticipants = resultSet.getInt("required_participants"),
                                    registeredParticipants = resultSet.getInt("registered_participants"),
                                    daysToStart = resultSet.getInt("days_to_start"),
                                    daysToJoinEnd = resultSet.getInt("days_to_join_end"),
                                    joinUntilLabel = resultSet.getString("join_until_label"),
                                    submitUntilLabel = resultSet.getString("submit_until_label"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem> {
        return withConnection { connection ->
            val userId = resolveDefaultUserId(connection) ?: return@withConnection emptyList()
            connection.prepareStatement(FETCH_CREATED_PROBLEMS_SQL).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val daysToJoinEnd = resultSet.getInt("days_to_join_end")
                            val daysToSubmitEnd = resultSet.getInt("days_to_submit_end")
                            val winnerWallet = resultSet.getString("winner_wallet")
                            val winnerWonAtLabel = resultSet.getString("winner_won_at_label")
                            val status = createdProblemStatus(
                                problemStatus = resultSet.getString("problem_status"),
                                daysToJoinEnd = daysToJoinEnd,
                                daysToSubmitEnd = daysToSubmitEnd,
                                winnerWallet = winnerWallet,
                            )
                            add(
                                CreatedProblem(
                                    id = resultSet.getLong("id").toInt(),
                                    title = resultSet.getString("title"),
                                    status = status,
                                    requiredParticipants = resultSet.getInt("required_participants"),
                                    registeredParticipants = resultSet.getInt("registered_participants"),
                                    submissions = resultSet.getInt("submissions"),
                                    startedOn = if (status == CreatedProblemStatus.Started) {
                                        resultSet.getString("started_on_label")
                                    } else {
                                        null
                                    },
                                    finishedOn = if (status == CreatedProblemStatus.Completed) {
                                        winnerWonAtLabel ?: resultSet.getString("finished_on_label")
                                    } else {
                                        null
                                    },
                                    registrationEnds = if (status == CreatedProblemStatus.Waiting) {
                                        resultSet.getString("registration_ends_label")
                                    } else {
                                        null
                                    },
                                    timeElapsed = if (status == CreatedProblemStatus.Expired) {
                                        resultSet.getString("time_elapsed_label")
                                    } else {
                                        null
                                    },
                                    winner = if (status == CreatedProblemStatus.Completed) {
                                        winnerWallet
                                    } else {
                                        null
                                    },
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem> {
        return withConnection { connection ->
            val userId = resolveDefaultUserId(connection) ?: return@withConnection emptyList()
            connection.prepareStatement(FETCH_PARTICIPATION_PROBLEMS_SQL).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val attemptsCount = resultSet.getInt("attempts_count")
                            val daysToSubmitEnd = resultSet.getInt("days_to_submit_end")
                            add(
                                ParticipationProblem(
                                    id = resultSet.getLong("id").toInt(),
                                    title = resultSet.getString("title"),
                                    status = if (attemptsCount > 0) {
                                        ParticipationStatus.Submitted
                                    } else {
                                        ParticipationStatus.NotSubmitted
                                    },
                                    timeLeftLabel = "${max(0, daysToSubmitEnd)}d",
                                    participants = resultSet.getInt("participants"),
                                    attemptsCount = attemptsCount,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        return DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use(block)
    }

    private fun applySchema(connection: Connection) {
        val schemaSql = javaClass.classLoader.getResource(SCHEMA_RESOURCE_PATH)?.readText()
            ?: error("Missing SQL resource '$SCHEMA_RESOURCE_PATH'.")
        connection.createStatement().use { statement ->
            statement.execute(schemaSql)
        }
    }

    private fun resolveDefaultUserId(connection: Connection): Long? {
        connection.prepareStatement(FETCH_DEFAULT_USER_ID_SQL).use { statement ->
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) {
                    resultSet.getLong("user_id")
                } else {
                    null
                }
            }
        }
    }

    private fun seedProblemsIfEmpty(connection: Connection) {
        val existingCount = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM problems").use { resultSet ->
                check(resultSet.next()) { "Failed to read problem count." }
                resultSet.getInt(1)
            }
        }

        if (existingCount > 0) {
            return
        }

        val userIds = (1..11).map { index ->
            insertUser(connection, walletAddressForSeed(index))
        }

        val firstProblemId = insertProblem(
            connection = connection,
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
            connection = connection,
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
            registerUser(connection, problemId = firstProblemId, userId = userId)
        }
        userIds.forEach { userId ->
            registerUser(connection, problemId = secondProblemId, userId = userId)
        }

        insertProblemSubmission(
            connection = connection,
            problemId = firstProblemId,
            userId = userIds[0],
            status = SubmissionAttemptStatus.Rejected,
        )
        insertProblemSubmission(
            connection = connection,
            problemId = firstProblemId,
            userId = userIds[0],
            status = SubmissionAttemptStatus.Accepted,
        )
        insertProblemSubmission(
            connection = connection,
            problemId = firstProblemId,
            userId = userIds[2],
            status = SubmissionAttemptStatus.Accepted,
        )
        insertProblemSubmission(
            connection = connection,
            problemId = secondProblemId,
            userId = userIds[4],
            status = SubmissionAttemptStatus.Error,
        )
        insertProblemSubmission(
            connection = connection,
            problemId = secondProblemId,
            userId = userIds[4],
            status = SubmissionAttemptStatus.Rejected,
        )
        insertProblemSubmission(
            connection = connection,
            problemId = secondProblemId,
            userId = userIds[4],
            status = SubmissionAttemptStatus.Accepted,
        )

        insertProblemWinner(
            connection = connection,
            problemId = firstProblemId,
            winnerUserId = userIds[2],
            payoutAmount = 25,
        )
        insertProblemWinner(
            connection = connection,
            problemId = secondProblemId,
            winnerUserId = userIds[4],
            payoutAmount = 40,
        )
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
        connection: Connection,
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
        connection.prepareStatement(INSERT_PROBLEM_SQL).use { statement ->
            statement.setString(1, title)
            statement.setString(2, description)
            statement.setString(3, problemStatus.dbValue)
            statement.setInt(4, prizeAmount)
            statement.setInt(5, entryFeeAmount)
            statement.setInt(6, requiredParticipants)
            statement.setObject(7, joinUntilDate)
            statement.setObject(8, submitUntilDate)
            statement.setLong(9, createdByUserId)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "Failed to insert problem '$title'." }
                return resultSet.getLong("problem_id")
            }
        }
    }

    private fun insertUser(connection: Connection, walletAddress: String): Long {
        connection.prepareStatement(INSERT_USER_SQL).use { statement ->
            statement.setString(1, walletAddress)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "Failed to insert user '$walletAddress'." }
                return resultSet.getLong("user_id")
            }
        }
    }

    private fun registerUser(connection: Connection, problemId: Long, userId: Long) {
        connection.prepareStatement(INSERT_PROBLEM_PARTICIPANT_SQL).use { statement ->
            statement.setLong(1, problemId)
            statement.setLong(2, userId)
            statement.executeUpdate()
        }
    }

    private fun insertProblemSubmission(
        connection: Connection,
        problemId: Long,
        userId: Long,
        status: SubmissionAttemptStatus,
    ) {
        connection.prepareStatement(INSERT_PROBLEM_SUBMISSION_SQL).use { statement ->
            statement.setLong(1, problemId)
            statement.setLong(2, userId)
            statement.setString(3, status.dbValue)
            statement.executeUpdate()
        }
    }

    private fun insertProblemWinner(
        connection: Connection,
        problemId: Long,
        winnerUserId: Long,
        payoutAmount: Int,
    ) {
        connection.prepareStatement(INSERT_PROBLEM_WINNER_SQL).use { statement ->
            statement.setLong(1, problemId)
            statement.setLong(2, winnerUserId)
            statement.setInt(3, payoutAmount)
            statement.executeUpdate()
        }
    }

    private fun walletAddressForSeed(index: Int): String {
        return "0x${index.toString(16).padStart(40, '0')}"
    }
}

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

private const val SCHEMA_RESOURCE_PATH = "db/schema.sql"

private const val FETCH_DEFAULT_USER_ID_SQL = """
SELECT user_id
FROM users
ORDER BY user_id ASC
LIMIT 1
"""

private const val FETCH_PROBLEMS_SQL = """
SELECT
    p.problem_id AS id,
    p.title,
    p.description,
    p.prize_amount,
    p.entry_fee_amount,
    p.required_participants,
    COALESCE(COUNT(pp.user_id), 0)::int AS registered_participants,
    GREATEST(0, p.join_until_date - CURRENT_DATE) AS days_to_start,
    GREATEST(0, p.join_until_date - CURRENT_DATE) AS days_to_join_end,
    TO_CHAR(p.join_until_date, 'YYYY-MM-DD') AS join_until_label,
    TO_CHAR(p.submit_until_date, 'YYYY-MM-DD') AS submit_until_label
FROM problems p
LEFT JOIN problem_participants pp ON pp.problem_id = p.problem_id
WHERE p.problem_status = 'open'
GROUP BY
    p.problem_id,
    p.title,
    p.description,
    p.prize_amount,
    p.entry_fee_amount,
    p.required_participants,
    p.join_until_date,
    p.submit_until_date
ORDER BY p.created_at DESC, p.problem_id DESC
"""

private const val FETCH_CREATED_PROBLEMS_SQL = """
SELECT
    p.problem_id AS id,
    p.problem_status,
    p.title,
    p.required_participants,
    COALESCE(participant_counts.registered_participants, 0)::int AS registered_participants,
    COALESCE(submission_counts.submissions, 0)::int AS submissions,
    (p.join_until_date - CURRENT_DATE)::int AS days_to_join_end,
    (p.submit_until_date - CURRENT_DATE)::int AS days_to_submit_end,
    TO_CHAR(p.join_until_date, 'YYYY-MM-DD') AS registration_ends_label,
    TO_CHAR(p.join_until_date, 'YYYY-MM-DD') AS started_on_label,
    TO_CHAR(p.submit_until_date, 'YYYY-MM-DD') AS finished_on_label,
    TO_CHAR(p.submit_until_date, 'YYYY-MM-DD') AS time_elapsed_label,
    winner.winner_wallet,
    winner.winner_won_at_label
FROM problems p
LEFT JOIN (
    SELECT problem_id, COUNT(*)::int AS registered_participants
    FROM problem_participants
    GROUP BY problem_id
) participant_counts ON participant_counts.problem_id = p.problem_id
LEFT JOIN (
    SELECT problem_id, COUNT(*)::int AS submissions
    FROM problem_submissions
    GROUP BY problem_id
) submission_counts ON submission_counts.problem_id = p.problem_id
LEFT JOIN LATERAL (
    SELECT
        u.wallet_address AS winner_wallet,
        TO_CHAR(pw.won_at, 'YYYY-MM-DD') AS winner_won_at_label
    FROM problem_winners pw
    JOIN users u ON u.user_id = pw.winner_user_id
    WHERE pw.problem_id = p.problem_id
    ORDER BY pw.won_at DESC, pw.winner_user_id DESC
    LIMIT 1
) winner ON TRUE
WHERE p.created_by_user_id = ?
ORDER BY p.created_at DESC, p.problem_id DESC
"""

private const val FETCH_PARTICIPATION_PROBLEMS_SQL = """
SELECT
    p.problem_id AS id,
    p.title,
    COALESCE(participant_counts.participants, 0)::int AS participants,
    (p.submit_until_date - CURRENT_DATE)::int AS days_to_submit_end,
    COALESCE(submission_stats.attempts_count, 0)::int AS attempts_count
FROM problem_participants pp
JOIN problems p ON p.problem_id = pp.problem_id
LEFT JOIN (
    SELECT problem_id, COUNT(*)::int AS participants
    FROM problem_participants
    GROUP BY problem_id
) participant_counts ON participant_counts.problem_id = p.problem_id
LEFT JOIN (
    SELECT problem_id, user_id, COUNT(*)::int AS attempts_count
    FROM problem_submissions
    GROUP BY problem_id, user_id
) submission_stats
    ON submission_stats.problem_id = pp.problem_id
   AND submission_stats.user_id = pp.user_id
WHERE pp.user_id = ?
ORDER BY p.created_at DESC, p.problem_id DESC
"""

private const val INSERT_PROBLEM_SQL = """
INSERT INTO problems (
    title,
    description,
    problem_status,
    prize_amount,
    entry_fee_amount,
    required_participants,
    join_until_date,
    submit_until_date,
    created_by_user_id
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
RETURNING problem_id
"""

private const val INSERT_USER_SQL = """
INSERT INTO users (wallet_address)
VALUES (?)
RETURNING user_id
"""

private const val INSERT_PROBLEM_PARTICIPANT_SQL = """
INSERT INTO problem_participants (
    problem_id,
    user_id
) VALUES (?, ?)
"""

private const val INSERT_PROBLEM_SUBMISSION_SQL = """
INSERT INTO problem_submissions (
    problem_id,
    user_id,
    status
) VALUES (?, ?, ?)
"""

private const val INSERT_PROBLEM_WINNER_SQL = """
INSERT INTO problem_winners (
    problem_id,
    winner_user_id,
    payout_amount
) VALUES (?, ?, ?)
"""
