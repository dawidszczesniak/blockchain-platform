package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppScrollbarShape = RoundedCornerShape(8.dp)
val AppScrollbarThickness = 10.dp
val AppScrollbarTrackColor = Color(0xFF4A4A4A)

fun appScrollbarStyle() = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = AppScrollbarThickness,
    shape = AppScrollbarShape,
    hoverDurationMillis = 0,
    unhoverColor = Color(0xFFBDBDBD),
    hoverColor = Color(0xFFBDBDBD),
)

@Composable
fun ProvideAppScrollbarStyle(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScrollbarStyle provides appScrollbarStyle()) {
        content()
    }
}

@Composable
fun AppScrollbarTrack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(AppScrollbarThickness)
            .background(
                color = AppScrollbarTrackColor,
                shape = AppScrollbarShape,
            ),
    )
}

@Composable
fun AppVerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = adapter,
        modifier = modifier.width(AppScrollbarThickness),
    )
}
