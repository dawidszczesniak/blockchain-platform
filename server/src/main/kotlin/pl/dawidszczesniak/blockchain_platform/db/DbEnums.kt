package pl.dawidszczesniak.blockchain_platform.db

internal enum class ProblemLifecycleStatus(val dbValue: String) {
    Open("open"),
    Closed("closed"),
}

internal enum class SubmissionAttemptStatus(val dbValue: String) {
    Accepted("accepted"),
    Rejected("rejected"),
    Error("error"),
}

internal enum class SubmissionTestResultStatus(val dbValue: String) {
    Passed("passed"),
    Failed("failed"),
    Error("error"),
    Timeout("timeout"),
}

internal enum class SubmissionAttestationStatus(val dbValue: String) {
    Ok("ok"),
    Error("error"),
    Invalid("invalid"),
}

internal enum class CompetitionSettlementStatus(val dbValue: String) {
    Pending("pending"),
    Settled("settled"),
    Cancelled("cancelled"),
    Failed("failed"),
    Disabled("disabled"),
}

internal enum class CompetitionSettlementJobType(val dbValue: String) {
    RegistrationDeadline("registration_deadline"),
    SubmissionDeadline("submission_deadline"),
}

internal enum class CompetitionSettlementJobStatus(val dbValue: String) {
    Scheduled("scheduled"),
    Running("running"),
    Completed("completed"),
    Dead("dead"),
}

internal enum class SubmissionJudgeJobStatus(val dbValue: String) {
    Queued("queued"),
    Running("running"),
    Accepted("accepted"),
    Rejected("rejected"),
    Error("error"),
}
