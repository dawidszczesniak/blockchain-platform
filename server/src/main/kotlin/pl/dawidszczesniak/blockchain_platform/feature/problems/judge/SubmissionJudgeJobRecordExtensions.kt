package pl.dawidszczesniak.blockchain_platform.feature.problems.judge

import pl.dawidszczesniak.blockchain_platform.db.SubmissionJudgeJobStatus

internal fun SubmissionJudgeJobRecord.isReceiptRetryable(): Boolean {
    return status == SubmissionJudgeJobStatus.Error &&
        submissionId != null &&
        !resultPayloadJson.isNullOrBlank() &&
        statusMessage.orEmpty().contains(RECEIPT_TIMEOUT_MARKER, ignoreCase = true)
}

internal fun SubmissionJudgeJobRecord.hasStoredReceiptRetryPayload(): Boolean {
    return submissionId != null && !resultPayloadJson.isNullOrBlank()
}

private const val RECEIPT_TIMEOUT_MARKER = "receipt was not confirmed in time"
