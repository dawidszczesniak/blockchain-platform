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
    val paymentAssetCode = varchar("payment_asset_code", length = 32)
    val prizeAmountAtomic = text("prize_amount")
    val entryFeeAmountAtomic = text("entry_fee_amount")
    val requiredParticipants = integer("required_participants")
    val onchainCompetitionId = long("onchain_competition_id").nullable()
    val onchainCreationKey = varchar("onchain_creation_key", length = 66).nullable()
    val onchainContractAddress = varchar("onchain_contract_address", length = 66).nullable()
    val onchainCreationTxHash = varchar("onchain_creation_tx_hash", length = 128).nullable()
    val onchainCreationFromWallet = varchar("onchain_creation_from_wallet", length = 66).nullable()
    val onchainCreationConfirmedAt = datetime("onchain_creation_confirmed_at").nullable()
    val onchainSettlementStatus = varchar("onchain_settlement_status", length = 16)
    val onchainSettlementTxHash = varchar("onchain_settlement_tx_hash", length = 128).nullable()
    val onchainSettlementFromWallet = varchar("onchain_settlement_from_wallet", length = 66).nullable()
    val onchainSettlementError = text("onchain_settlement_error").nullable()
    val onchainSettledAt = datetime("onchain_settled_at").nullable()
    val joinUntilDate = date("join_until_date")
    val submitUntilDate = date("submit_until_date")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(problemId)
}

internal object ProblemParticipantsTable : Table("problem_participants") {
    val problemId = long("problem_id")
    val userId = long("user_id")
    val joinTxHash = varchar("join_tx_hash", length = 128).nullable()
    val joinFromWallet = varchar("join_from_wallet", length = 66).nullable()
    val joinedOnchainAt = datetime("joined_onchain_at").nullable()
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
    val onchainRecordContractAddress = varchar("onchain_record_contract_address", length = 66).nullable()
    val onchainRecordTxHash = varchar("onchain_record_tx_hash", length = 128).nullable()
    val onchainRecordFromWallet = varchar("onchain_record_from_wallet", length = 66).nullable()
    val onchainRecordError = text("onchain_record_error").nullable()
    val onchainRecordedAt = datetime("onchain_recorded_at").nullable()
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

internal object CompetitionSettlementJobsTable : Table("competition_settlement_jobs") {
    val jobId = long("job_id").autoIncrement()
    val problemId = long("problem_id")
    val competitionId = long("competition_id")
    val jobType = varchar("job_type", length = 32)
    val status = varchar("status", length = 16)
    val attempts = integer("attempts")
    val runAt = datetime("run_at")
    val availableAt = datetime("available_at")
    val lockedAt = datetime("locked_at").nullable()
    val statusMessage = text("status_message").nullable()
    val completedAt = datetime("completed_at").nullable()
    val createdAt = datetime("created_at")

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

internal object ProblemWinnersTable : Table("problem_winners") {
    val problemId = long("problem_id")
    val winnerUserId = long("winner_user_id")
    val payoutAmountAtomic = text("payout_amount")
    val settlementTxHash = varchar("settlement_tx_hash", length = 128).nullable()
    val settlementFromWallet = varchar("settlement_from_wallet", length = 66).nullable()
    val wonAt = datetime("won_at")

    override val primaryKey = PrimaryKey(problemId, winnerUserId)
}

internal object DashboardDailyMetricsTable : Table("dashboard_daily_metrics") {
    val metricDate = date("metric_date")
    val activeChallenges = integer("active_challenges")
    val prizePoolLabel = text("prize_pool_amount")
    val submissionsCount = integer("submissions_count")

    override val primaryKey = PrimaryKey(metricDate)
}
