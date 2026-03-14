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
