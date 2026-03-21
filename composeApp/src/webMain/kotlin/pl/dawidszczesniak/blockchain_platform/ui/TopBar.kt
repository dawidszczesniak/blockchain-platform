package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.app_name
import blockchain_platform.composeapp.generated.resources.login
import blockchain_platform.composeapp.generated.resources.logout
import blockchain_platform.composeapp.generated.resources.menu
import blockchain_platform.composeapp.generated.resources.nav_create_problem
import blockchain_platform.composeapp.generated.resources.nav_home
import blockchain_platform.composeapp.generated.resources.nav_my_participation
import blockchain_platform.composeapp.generated.resources.nav_my_problems
import blockchain_platform.composeapp.generated.resources.nav_problem_list
import blockchain_platform.composeapp.generated.resources.profile
import blockchain_platform.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.navigation.Route

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
    val surfaceShape = RoundedCornerShape(0.dp)
    val menuShape = RoundedCornerShape(18.dp)
    val menuContainerColor = colors.surface
    val menuBorder = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
    val menuItemColors = MenuDefaults.itemColors(
        textColor = colors.onSurface,
        leadingIconColor = colors.onSurface,
        trailingIconColor = colors.onSurface
    )

    BoxWithConstraints {
        val isCompact = maxWidth < 900.dp
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = surfaceShape,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            color = colors.surface.copy(alpha = 0.95f)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onHomeClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "B",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(Res.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (isCompact) {
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { compactMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Menu,
                                    contentDescription = stringResource(Res.string.menu),
                                    tint = colors.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = compactMenuExpanded,
                                onDismissRequest = { compactMenuExpanded = false },
                                offset = DpOffset(x = 0.dp, y = 8.dp),
                                shape = menuShape,
                                containerColor = menuContainerColor,
                                tonalElevation = 0.dp,
                                shadowElevation = 12.dp,
                                border = menuBorder
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.nav_home)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onNavigate(Route.Home)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.nav_problem_list)) },
                                    colors = menuItemColors,
                                    onClick = {
                                        compactMenuExpanded = false
                                        onNavigate(Route.Problems)
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
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.nav_my_participation)) },
                                        colors = menuItemColors,
                                        onClick = {
                                            compactMenuExpanded = false
                                            onNavigate(Route.MyParticipation)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NavTab(
                                text = stringResource(Res.string.nav_home),
                                selected = currentRoute == Route.Home,
                                onClick = { onNavigate(Route.Home) }
                            )
                            Spacer(Modifier.width(8.dp))
                            NavTab(
                                text = stringResource(Res.string.nav_problem_list),
                                selected = currentRoute.isProblemsArea(),
                                onClick = { onNavigate(Route.Problems) }
                            )
                            Spacer(Modifier.width(8.dp))
                            NavTab(
                                text = stringResource(Res.string.nav_create_problem),
                                selected = currentRoute == Route.CreateProblem,
                                onClick = { onNavigate(Route.CreateProblem) }
                            )
                            if (isLoggedIn) {
                                Spacer(Modifier.width(8.dp))
                                NavTab(
                                    text = stringResource(Res.string.nav_my_problems),
                                    selected = currentRoute == Route.MyProblems,
                                    onClick = { onNavigate(Route.MyProblems) }
                                )
                                Spacer(Modifier.width(8.dp))
                                NavTab(
                                    text = stringResource(Res.string.nav_my_participation),
                                    selected = currentRoute == Route.MyParticipation,
                                    onClick = { onNavigate(Route.MyParticipation) }
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        if (isLoggedIn) {
                            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                                IconButton(onClick = { profileMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = stringResource(Res.string.profile),
                                        tint = colors.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = profileMenuExpanded,
                                    onDismissRequest = { profileMenuExpanded = false },
                                    offset = DpOffset(x = 0.dp, y = 8.dp),
                                    shape = menuShape,
                                    containerColor = menuContainerColor,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 12.dp,
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
                            Button(
                                onClick = { onLoginClick() },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                            ) {
                                Text(stringResource(Res.string.login))
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    }
}

private fun Route.isProblemsArea(): Boolean {
    return this == Route.Problems || this is Route.ProblemDetails
}

@Composable
private fun NavTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    val background = if (selected) colors.surfaceVariant else Color.Transparent
    val borderColor = if (selected) colors.outline.copy(alpha = 0.6f) else Color.Transparent

    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant
        )
    }
}
