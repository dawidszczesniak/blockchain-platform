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
    val constraintsText = text("constraints_text")
    val examplesJson = text("examples_json")
    val referenceSolutionHash = varchar("reference_solution_hash", length = 66)
    val validationNodeId = varchar("validation_node_id", length = 128).nullable()
    val validationRunHash = varchar("validation_run_hash", length = 66).nullable()
    val validationResultHash = varchar("validation_result_hash", length = 66).nullable()
    val validationImageHash = varchar("validation_image_hash", length = 128).nullable()
    val validatedAt = datetime("validated_at").nullable()
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
    val validatorLanguage = varchar("validator_language", length = 32)
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
    val codeHash = varchar("code_hash", length = 66)
    val testsHash = varchar("tests_hash", length = 66)
    val resultHash = varchar("result_hash", length = 66)
    val consensusImageHash = varchar("consensus_image_hash", length = 128).nullable()
    val consensusNodes = integer("consensus_nodes")
    val commitmentHash = varchar("commitment_hash", length = 66)
    val runtimeMs = integer("runtime_ms")
    val memoryUsedKb = integer("memory_used_kb").nullable()
    val anchorStatus = varchar("anchor_status", length = 16)
    val anchorBatchId = long("anchor_batch_id").nullable()
    val anchorMerkleRoot = varchar("anchor_merkle_root", length = 66).nullable()
    val anchorMerkleProofJson = text("anchor_merkle_proof_json")
    val anchorTxHash = varchar("anchor_tx_hash", length = 128).nullable()
    val anchorError = text("anchor_error").nullable()
    val anchoredAt = datetime("anchored_at").nullable()
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

internal object ProblemSubmissionJudgeJobsTable : Table("problem_submission_judge_jobs") {
    val jobId = long("job_id").autoIncrement()
    val problemId = long("problem_id")
    val userId = long("user_id")
    val sourceCode = text("source_code")
    val language = varchar("language", length = 32)
    val status = varchar("status", length = 16)
    val statusMessage = text("status_message").nullable()
    val resultPayloadJson = text("result_payload_json").nullable()
    val previewPayloadJson = text("preview_payload_json").nullable()
    val submissionId = long("submission_id").nullable()
    val requestedAt = datetime("requested_at")
    val startedAt = datetime("started_at").nullable()
    val completedAt = datetime("completed_at").nullable()

    override val primaryKey = PrimaryKey(jobId)
}

internal object ProblemSubmissionAttestationsTable : Table("problem_submission_attestations") {
    val submissionId = long("submission_id")
    val nodeId = varchar("node_id", length = 128)
    val nodeUrl = text("node_url")
    val imageHash = varchar("image_hash", length = 128).nullable()
    val runHash = varchar("run_hash", length = 66).nullable()
    val resultHash = varchar("result_hash", length = 66).nullable()
    val attestationPayloadHash = varchar("attestation_payload_hash", length = 66).nullable()
    val attestationSignature = varchar("attestation_signature", length = 256).nullable()
    val attestationScheme = varchar("attestation_scheme", length = 32)
    val isValid = bool("is_valid")
    val isConsensus = bool("is_consensus")
    val nodeStatus = varchar("node_status", length = 16)
    val message = text("message").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(submissionId, nodeId)
}

internal object SubmissionAnchorBatchesTable : Table("submission_anchor_batches") {
    val batchId = long("batch_id").autoIncrement()
    val merkleRootHash = varchar("merkle_root_hash", length = 66)
    val leavesCount = integer("leaves_count")
    val fromSubmissionId = long("from_submission_id")
    val toSubmissionId = long("to_submission_id")
    val chainId = long("chain_id").nullable()
    val contractAddress = varchar("contract_address", length = 66).nullable()
    val txHash = varchar("tx_hash", length = 128).nullable()
    val status = varchar("status", length = 16)
    val failureReason = text("failure_reason").nullable()
    val createdAt = datetime("created_at")
    val anchoredAt = datetime("anchored_at").nullable()

    override val primaryKey = PrimaryKey(batchId)
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
