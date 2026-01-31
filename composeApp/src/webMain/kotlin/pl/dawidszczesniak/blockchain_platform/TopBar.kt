package pl.dawidszczesniak.blockchain_platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.app_name
import blockchain_platform.composeapp.generated.resources.login
import blockchain_platform.composeapp.generated.resources.logout
import blockchain_platform.composeapp.generated.resources.menu
import blockchain_platform.composeapp.generated.resources.nav_create_problem
import blockchain_platform.composeapp.generated.resources.nav_my_problems
import blockchain_platform.composeapp.generated.resources.nav_problem_list
import blockchain_platform.composeapp.generated.resources.profile
import blockchain_platform.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    currentRoute: Route,
    onNavigate: (Route) -> Unit,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    onHomeClick: () -> Unit,
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var compactMenuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val surfaceShape = RoundedCornerShape(22.dp)
    val menuShape = RoundedCornerShape(16.dp)
    val menuContainerColor = colors.surfaceVariant.copy(alpha = 0.96f)
    val menuBorder = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f))
    val menuItemColors = MenuDefaults.itemColors(
        textColor = colors.onSurface,
        leadingIconColor = colors.primary,
        trailingIconColor = colors.primary
    )

    BoxWithConstraints {
        val isCompact = maxWidth < 900.dp
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .border(1.dp, colors.outline.copy(alpha = 0.6f), surfaceShape),
            shape = surfaceShape,
            shadowElevation = 12.dp,
            tonalElevation = 6.dp,
            color = colors.surface.copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.app_name),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onHomeClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.weight(1f))
                if (isCompact) {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { compactMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = stringResource(Res.string.menu),
                                tint = colors.primary
                            )
                        }
                        DropdownMenu(
                            expanded = compactMenuExpanded,
                            onDismissRequest = { compactMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp),
                            shape = menuShape,
                            containerColor = menuContainerColor,
                            tonalElevation = 4.dp,
                            shadowElevation = 10.dp,
                            border = menuBorder
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.nav_problem_list)) },
                                colors = menuItemColors,
                                onClick = {
                                    compactMenuExpanded = false
                                    onHomeClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.nav_create_problem)) },
                                colors = menuItemColors,
                                onClick = {
                                    compactMenuExpanded = false
                                    onNavigate(Route.CreateProblem)
                                }
                            )
                            if (isLoggedIn) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.nav_my_problems)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onNavigate(Route.MyProblems)
                                    }
                                )
                            }
                            if (isLoggedIn) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.settings)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onNavigate(Route.Settings)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logout)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onLogout()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.login)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onLoginClick()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    NavTab(
                        text = stringResource(Res.string.nav_problem_list),
                        selected = currentRoute == Route.Problems,
                        onClick = { onHomeClick() }
                    )
                    Spacer(Modifier.width(8.dp))
                    NavTab(
                        text = stringResource(Res.string.nav_create_problem),
                        selected = currentRoute == Route.CreateProblem,
                        onClick = { onNavigate(Route.CreateProblem) }
                    )
                    Spacer(Modifier.width(12.dp))
                    if (isLoggedIn) {
                        NavTab(
                            text = stringResource(Res.string.nav_my_problems),
                            selected = currentRoute == Route.MyProblems,
                            onClick = { onNavigate(Route.MyProblems) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { profileMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = stringResource(Res.string.profile),
                                    tint = colors.primary
                                )
                            }
                        DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp),
                            shape = menuShape,
                            containerColor = menuContainerColor,
                            tonalElevation = 4.dp,
                            shadowElevation = 10.dp,
                            border = menuBorder
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.settings)) },
                                colors = menuItemColors,
                                onClick = {
                                    profileMenuExpanded = false
                                    onNavigate(Route.Settings)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.logout)) },
                                colors = menuItemColors,
                                onClick = {
                                    profileMenuExpanded = false
                                    onLogout()
                                }
                            )
                        }
                        }
                    } else {
                        NavTab(
                            text = stringResource(Res.string.login),
                            selected = currentRoute == Route.Login,
                            onClick = { onLoginClick() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    val gradient = Brush.horizontalGradient(
        colors = listOf(colors.primary, colors.tertiary)
    )
    val background = if (selected) gradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))

    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary.copy(alpha = 0.6f) else colors.outline.copy(alpha = 0.5f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurface
        )
    }
}
