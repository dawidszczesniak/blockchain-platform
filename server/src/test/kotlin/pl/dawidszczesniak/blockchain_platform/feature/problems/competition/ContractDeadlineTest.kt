package pl.dawidszczesniak.blockchain_platform.feature.problems.competition

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractDeadlineTest {
    @Test
    fun `uses end of utc day as contract deadline`() {
        val date = LocalDate.parse("2026-05-02")

        assertEquals(1_777_766_399L, date.toContractDeadlineEpochSeconds())
    }

    @Test
    fun `deadline is not passed before or at final second`() {
        val date = LocalDate.parse("2026-05-02")

        assertFalse(Instant.parse("2026-05-02T12:00:00Z").hasContractDeadlinePassed(date))
        assertFalse(Instant.parse("2026-05-02T23:59:59Z").hasContractDeadlinePassed(date))
    }

    @Test
    fun `deadline is passed after final second`() {
        val date = LocalDate.parse("2026-05-02")

        assertTrue(Instant.parse("2026-05-03T00:00:00Z").hasContractDeadlinePassed(date))
    }
}
