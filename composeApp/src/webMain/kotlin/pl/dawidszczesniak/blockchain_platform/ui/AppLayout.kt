package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.backend_unavailable_body
import blockchain_platform.composeapp.generated.resources.backend_unavailable_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = colors.background)
    }
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
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    surfaceAlpha: Float = 1f,
    borderAlpha: Float = 1f,
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
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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

@Composable
fun BackendStatusBanner(
    isAvailable: Boolean?,
    modifier: Modifier = Modifier,
) {
    if (isAvailable != false) return

    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colors.error, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.backend_unavailable_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface
            )
            Text(
                text = stringResource(Res.string.backend_unavailable_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}
