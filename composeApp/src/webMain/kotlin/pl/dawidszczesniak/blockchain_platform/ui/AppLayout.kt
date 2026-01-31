package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val gridColor = colors.onBackground.copy(alpha = 0.035f)
    val glowPrimary = colors.primary.copy(alpha = 0.25f)
    val glowSecondary = colors.secondary.copy(alpha = 0.2f)
    val lineBaseColor = colors.primary
    val nodeBaseColor = colors.secondary

    val nodes = remember { generateNetworkNodes(120, seed = 17) }
    val edges = remember {
        buildNetworkEdges(
            nodes = nodes,
            seed = 99,
            maxConnections = 3,
            maxDist = 0.28f,
            keepRatio = 0.9f,
        )
    }

    var frameNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameNanos = it }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    colors.background,
                    colors.surfaceVariant,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glowPrimary, Color.Transparent),
                center = Offset(size.width * 0.2f, size.height * 0.25f),
                radius = size.minDimension * 0.7f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glowSecondary, Color.Transparent),
                center = Offset(size.width * 0.75f, size.height * 0.2f),
                radius = size.minDimension * 0.6f
            )
        )

        val t = frameNanos / 1_000_000_000f
        val positions = nodes.map { node ->
            val wobbleX = sin(t * node.speed + node.phase) * node.driftX
            val wobbleY = cos(t * node.speed * 0.85f + node.phase) * node.driftY
            val x = (node.baseX + wobbleX).coerceIn(0.02f, 0.98f) * size.width
            val y = (node.baseY + wobbleY).coerceIn(0.02f, 0.98f) * size.height
            Offset(x, y)
        }

        edges.forEach { edge ->
            val p1 = positions[edge.a]
            val p2 = positions[edge.b]
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            val dist = sqrt(dx * dx + dy * dy)
            val falloff = (1f - dist / (size.minDimension * 0.55f)).coerceIn(0f, 1f)
            val pulse = 0.6f + 0.4f * sin(t * 1.1f + edge.phase * 6.28f)
            val alpha = falloff * pulse * 0.35f
            if (alpha > 0.02f) {
                drawLine(
                    color = lineBaseColor.copy(alpha = alpha),
                    start = p1,
                    end = p2,
                    strokeWidth = edge.thickness
                )
            }
            if (edge.pulse && alpha > 0.04f) {
                val progress = ((t * edge.speed + edge.phase) % 1f).coerceIn(0f, 1f)
                val pulsePos = lerp(p1, p2, progress)
                val pulseAlpha = (alpha * 1.05f).coerceIn(0f, 1f)
                drawCircle(
                    color = lineBaseColor.copy(alpha = pulseAlpha),
                    radius = 2.2f + edge.thickness,
                    center = pulsePos
                )
            }
        }

        nodes.forEachIndexed { index, node ->
            val pulse = 0.6f + 0.4f * sin(t * node.speed + node.phase)
            val radius = (node.radius * (1f + 0.25f * pulse)).dp.toPx()
            drawCircle(
                color = nodeBaseColor.copy(alpha = 0.28f * pulse),
                radius = radius * 2.4f,
                center = positions[index]
            )
            drawCircle(
                color = nodeBaseColor.copy(alpha = 0.7f * pulse),
                radius = radius,
                center = positions[index]
            )
        }

        val step = 140.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += step
        }
    }
}

private data class NetworkNode(
    val baseX: Float,
    val baseY: Float,
    val driftX: Float,
    val driftY: Float,
    val phase: Float,
    val speed: Float,
    val radius: Float,
)

private data class NetworkEdge(
    val a: Int,
    val b: Int,
    val phase: Float,
    val speed: Float,
    val thickness: Float,
    val pulse: Boolean,
)

private fun generateNetworkNodes(count: Int, seed: Int): List<NetworkNode> {
    val rng = Random(seed)
    return List(count) {
        NetworkNode(
            baseX = rng.nextFloat() * 0.9f + 0.05f,
            baseY = rng.nextFloat() * 0.9f + 0.05f,
            driftX = rng.nextFloat() * 0.02f + 0.01f,
            driftY = rng.nextFloat() * 0.02f + 0.01f,
            phase = rng.nextFloat() * (2f * PI.toFloat()),
            speed = rng.nextFloat() * 0.6f + 0.35f,
            radius = rng.nextFloat() * 1.6f + 1.2f
        )
    }
}

private fun buildNetworkEdges(
    nodes: List<NetworkNode>,
    seed: Int,
    maxConnections: Int,
    maxDist: Float,
    keepRatio: Float
): List<NetworkEdge> {
    val rng = Random(seed)
    val edges = mutableSetOf<Long>()
    val result = mutableListOf<NetworkEdge>()
    for (i in nodes.indices) {
        val distances = nodes.indices
            .filter { it != i }
            .map { j ->
                val dx = nodes[i].baseX - nodes[j].baseX
                val dy = nodes[i].baseY - nodes[j].baseY
                val d = sqrt(dx * dx + dy * dy)
                j to d
            }
            .sortedBy { it.second }
        var connected = 0
        for ((j, d) in distances) {
            if (d > maxDist) break
            val a = minOf(i, j)
            val b = maxOf(i, j)
            val key = (a.toLong() shl 32) or b.toLong()
            if (edges.add(key)) {
                if (rng.nextFloat() <= keepRatio) {
                    result.add(
                        NetworkEdge(
                            a = a,
                            b = b,
                            phase = rng.nextFloat(),
                            speed = rng.nextFloat() * 0.9f + 0.35f,
                            thickness = rng.nextFloat() * 0.9f + 0.9f,
                            pulse = rng.nextFloat() < 0.5f
                        )
                    )
                }
                connected++
                if (connected >= maxConnections) break
            }
        }
    }
    return result
}

private fun lerp(start: Offset, end: Offset, t: Float): Offset {
    val clamped = t.coerceIn(0f, 1f)
    return Offset(
        x = start.x + (end.x - start.x) * clamped,
        y = start.y + (end.y - start.y) * clamped
    )
}

@Composable
fun AppPageContainer(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 28.dp),
    maxWidth: Dp = 1200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontal = if (this.maxWidth < 760.dp) 16.dp else 28.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = horizontal)
                .widthIn(max = maxWidth)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content
        )
    }
}

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    surfaceAlpha: Float = 0.86f,
    borderAlpha: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = borderAlpha),
                shape = shape
            ),
        shape = shape,
        color = colors.surface.copy(alpha = surfaceAlpha),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}
