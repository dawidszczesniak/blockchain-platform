package pl.dawidszczesniak.blockchain_platform.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

internal object UsersTable : Table("users") {
    val userId = long("user_id").autoIncrement()
    val walletAddress = varchar("wallet_address", length = 66)
    val registeredAt = datetime("registered_at")
    val lastLoginAt = datetime("last_login_at")

    override val primaryKey = PrimaryKey(userId)
}

internal object ProblemsTable : Table("problems") {
    val problemId = long("problem_id").autoIncrement()
    val createdByUserId = long("created_by_user_id")
    val problemStatus = varchar("problem_status", length = 16)
    val title = text("title")
    val description = text("description")
    val prizeAmount = long("prize_amount")
    val entryFeeAmount = long("entry_fee_amount")
    val requiredParticipants = integer("required_participants")
    val joinUntilDate = date("join_until_date")
    val submitUntilDate = date("submit_until_date")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(problemId)
}

internal object ProblemParticipantsTable : Table("problem_participants") {
    val problemId = long("problem_id")
    val userId = long("user_id")
    val registeredAt = datetime("registered_at")

    override val primaryKey = PrimaryKey(problemId, userId)
}

internal object ProblemTestsTable : Table("problem_tests") {
    val problemTestId = long("problem_test_id").autoIncrement()
    val problemId = long("problem_id")
    val testOrder = integer("test_order")
    val inputData = text("input_data")
    val expectedOutput = text("expected_output")
    val validatorCode = text("validator_code")
    val isHidden = bool("is_hidden")
    val timeoutMs = integer("timeout_ms")
    val memoryLimitMb = integer("memory_limit_mb")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(problemTestId)
}

internal object ProblemSubmissionsTable : Table("problem_submissions") {
    val submissionId = long("submission_id").autoIncrement()
    val problemId = long("problem_id")
    val userId = long("user_id")
    val status = varchar("status", length = 16)
    val sourceCode = text("source_code")
    val language = varchar("language", length = 32)
    val submittedAt = datetime("submitted_at")

    override val primaryKey = PrimaryKey(submissionId)
}

internal object ProblemSubmissionTestResultsTable : Table("problem_submission_test_results") {
    val submissionId = long("submission_id")
    val problemTestId = long("problem_test_id")
    val resultStatus = varchar("result_status", length = 16)
    val executionTimeMs = integer("execution_time_ms")
    val memoryUsedKb = integer("memory_used_kb").nullable()
    val message = text("message").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(submissionId, problemTestId)
}

internal object ProblemWinnersTable : Table("problem_winners") {
    val problemId = long("problem_id")
    val winnerUserId = long("winner_user_id")
    val payoutAmount = long("payout_amount")
    val wonAt = datetime("won_at")

    override val primaryKey = PrimaryKey(problemId, winnerUserId)
}

internal object DashboardDailyMetricsTable : Table("dashboard_daily_metrics") {
    val metricDate = date("metric_date")
    val activeChallenges = integer("active_challenges")
    val prizePoolAmount = long("prize_pool_amount")
    val submissionsCount = integer("submissions_count")

    override val primaryKey = PrimaryKey(metricDate)
}
