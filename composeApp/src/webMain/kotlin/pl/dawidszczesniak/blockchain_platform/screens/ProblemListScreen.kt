package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.min

private const val PAGE_SIZE = 20
private const val LOAD_MORE_THRESHOLD = 5
private const val INITIAL_CREATED_ORDER = 1000

private data class FakeProblem(
    val id: Int,
    val createdOrder: Int,
    val title: String,
    val prizeAmount: Int,
    val entryFeeAmount: Int,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
)

private enum class SortOption(val label: String) {
    Newest("Najnowsze dodane"),
    Oldest("Najstarsze dodane"),

    StartSoonest("Najmniej dni do startu"),
    StartLatest("Najwięcej dni do startu"),

    PrizeHighest("Największa nagroda"),
    PrizeLowest("Najmniejsza nagroda"),

    EntryFeeHighest("Największa wejściówka"),
    EntryFeeLowest("Najmniejsza wejściówka"),

    RequiredMost("Najwięcej wymaganych uczestników"),
    RequiredLeast("Najmniej wymaganych uczestników"),

    RegisteredMost("Najwięcej zapisanych"),
    RegisteredLeast("Najmniej zapisanych"),

    ProgressMost("Największy progres"),
    ProgressLeast("Najmniejszy progres"),

    JoinEndsSoonest("Rejestracja kończy się najszybciej"),
    JoinEndsLatest("Rejestracja kończy się najpóźniej"),
}

@Composable
fun ProblemsListScreen() {
    val listState = rememberLazyListState()

    var allProblems by remember {
        mutableStateOf(
            generateFakeProblems(
                startId = 1,
                startCreatedOrder = INITIAL_CREATED_ORDER,
                count = PAGE_SIZE
            )
        )
    }
    var nextId by remember { mutableStateOf(1 + PAGE_SIZE) }
    var nextCreatedOrder by remember { mutableStateOf(INITIAL_CREATED_ORDER - PAGE_SIZE) }

    var sortOption by remember { mutableStateOf(SortOption.Newest) }
    val sortedProblems = remember(allProblems, sortOption) {
        sortProblems(allProblems, sortOption)
    }

    var isLoadingMore by remember { mutableStateOf(false) }

    suspend fun loadMoreMock() {
        if (isLoadingMore) return
        isLoadingMore = true

        delay(350)

        val more = generateFakeProblems(
            startId = nextId,
            startCreatedOrder = nextCreatedOrder,
            count = PAGE_SIZE
        )

        allProblems = allProblems + more
        nextId += PAGE_SIZE
        nextCreatedOrder -= PAGE_SIZE

        isLoadingMore = false
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalItems
        }
            .map { (lastVisible, total) ->
                total > 0 && lastVisible >= (total - LOAD_MORE_THRESHOLD)
            }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    loadMoreMock()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Lista problemów", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Mock: ${allProblems.size} problemów (infinite scroll + sortowanie).",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            SortRow(
                sortOption = sortOption,
                onSortChanged = { sortOption = it }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 52.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedProblems) { p ->
                    ProblemCard(problem = p, onOpen = { /* TODO */ })
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
            ) {
                val shape = RoundedCornerShape(8.dp)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = shape
                        )
                )

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                )
            }

            if (isLoadingMore) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Dociąganie kolejnych problemów…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SortRow(
    sortOption: SortOption,
    onSortChanged: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text("Sortowanie:", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(10.dp))

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(sortOption.label)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = 6.dp)
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSortChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemCard(
    problem: FakeProblem,
    onOpen: () -> Unit
) {
    val required = max(1, problem.requiredParticipants)
    val registered = min(problem.registeredParticipants, required)
    val progress = registered.toFloat() / required.toFloat()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(problem.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(label = "Nagroda", value = "${problem.prizeAmount} USDC")
                InfoChip(label = "Wejściówka", value = "${problem.entryFeeAmount} USDC")
                InfoChip(label = "Start", value = "${problem.daysToStart} dni")
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Uczestnicy: $registered / ${problem.requiredParticipants} (wymagani)",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Rejestracja do: ${problem.joinUntilLabel} (za ${problem.daysToJoinEnd} dni)  |  Zgłoszenia do: ${problem.submitUntilLabel}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onOpen, enabled = false) {
                    Text("Szczegóły (wkrótce)")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    AssistChip(
        onClick = { },
        label = { Text("$label: $value") }
    )
}

private fun sortProblems(list: List<FakeProblem>, option: SortOption): List<FakeProblem> {
    return when (option) {
        SortOption.Newest -> list.sortedByDescending { it.createdOrder }
        SortOption.Oldest -> list.sortedBy { it.createdOrder }

        SortOption.StartSoonest -> list.sortedBy { it.daysToStart }
        SortOption.StartLatest -> list.sortedByDescending { it.daysToStart }

        SortOption.PrizeHighest -> list.sortedByDescending { it.prizeAmount }
        SortOption.PrizeLowest -> list.sortedBy { it.prizeAmount }

        SortOption.EntryFeeHighest -> list.sortedByDescending { it.entryFeeAmount }
        SortOption.EntryFeeLowest -> list.sortedBy { it.entryFeeAmount }

        SortOption.RequiredMost -> list.sortedByDescending { it.requiredParticipants }
        SortOption.RequiredLeast -> list.sortedBy { it.requiredParticipants }

        SortOption.RegisteredMost -> list.sortedByDescending { it.registeredParticipants }
        SortOption.RegisteredLeast -> list.sortedBy { it.registeredParticipants }

        SortOption.ProgressMost -> list.sortedByDescending { progressRatio(it) }
        SortOption.ProgressLeast -> list.sortedBy { progressRatio(it) }

        SortOption.JoinEndsSoonest -> list.sortedBy { it.daysToJoinEnd }
        SortOption.JoinEndsLatest -> list.sortedByDescending { it.daysToJoinEnd }
    }
}

private fun progressRatio(p: FakeProblem): Float {
    val required = max(1, p.requiredParticipants)
    val registered = min(p.registeredParticipants, required)
    return registered.toFloat() / required.toFloat()
}

private fun generateFakeProblems(
    startId: Int,
    startCreatedOrder: Int,
    count: Int
): List<FakeProblem> {
    return List(count) { i ->
        val id = startId + i
        val createdOrder = startCreatedOrder - i

        val required = 10 + ((id + createdOrder) % 11)
        val registered = min(required, (id * 3 + createdOrder) % (required + 1))

        val prize = 10 + ((id + 2) % 10)
        val entry = 1 + ((id + 1) % 5)

        val daysToJoinEnd = 1 + ((id + createdOrder) % 14)
        val daysToStart = daysToJoinEnd

        val joinDay = (10 + (id % 18)).toString().padStart(2, '0')
        val submitDay = (1 + (id % 20)).toString().padStart(2, '0')

        FakeProblem(
            id = id,
            createdOrder = createdOrder,
            title = "Problem #$id – Optymalizacja algorytmu (${('A' + (id % 26))})",
            prizeAmount = prize,
            entryFeeAmount = entry,
            requiredParticipants = required,
            registeredParticipants = registered,
            daysToStart = daysToStart,
            daysToJoinEnd = daysToJoinEnd,
            joinUntilLabel = "2026-02-$joinDay",
            submitUntilLabel = "2026-03-$submitDay"
        )
    }
}
