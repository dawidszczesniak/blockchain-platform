package pl.dawidszczesniak.blockchain_platform.feature.problems.competition

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

internal fun LocalDate.toContractDeadlineEpochSeconds(): Long {
    return atTime(LocalTime.of(23, 59, 59))
        .toEpochSecond(ZoneOffset.UTC)
}

internal fun Instant.hasContractDeadlinePassed(deadlineDate: LocalDate): Boolean {
    return epochSecond > deadlineDate.toContractDeadlineEpochSeconds()
}
