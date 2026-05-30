package pl.dawidszczesniak.blockchain_platform.feature.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.network_offline_body
import blockchain_platform.composeapp.generated.resources.network_offline_title
import blockchain_platform.composeapp.generated.resources.network_offline_waiting
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun NetworkOfflineScreen(modifier: Modifier = Modifier) {
    val dotsCount = rememberOfflineLoadingDotsCount()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.network_offline_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = offlineWaitingLabelWithDots(
                        message = stringResource(Res.string.network_offline_waiting),
                        loadingDotsCount = dotsCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.network_offline_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun rememberOfflineLoadingDotsCount(): Int {
    var dotsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            dotsCount = (dotsCount + 1) % 4
            delay(350)
        }
    }
    return dotsCount
}

@Composable
private fun offlineWaitingLabelWithDots(
    message: String,
    loadingDotsCount: Int,
) = buildAnnotatedString {
    append(message.trimEnd().removeSuffix("...").trimEnd('.').trimEnd())
    append(" ")
    repeat(3) { index ->
        withStyle(
            SpanStyle(
                color = if (index < loadingDotsCount) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Transparent
                },
            ),
        ) {
            append(".")
        }
    }
}
