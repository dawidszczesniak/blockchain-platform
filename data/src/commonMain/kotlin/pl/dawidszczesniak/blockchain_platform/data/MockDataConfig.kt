package pl.dawidszczesniak.blockchain_platform.data

// Central place to control mock list sizes and empty-state toggles.
data class MockDataConfig(
    var showEmptyProblemList: Boolean = true,
    var showEmptyCreatedList: Boolean = true,
    var showEmptyParticipationList: Boolean = true,
    var totalProblems: Int = 87,
    var totalCreated: Int = 80,
    var totalParticipations: Int = 80,
)
