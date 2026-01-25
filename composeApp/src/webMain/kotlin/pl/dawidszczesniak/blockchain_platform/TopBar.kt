package pl.dawidszczesniak.blockchain_platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.app_name
import blockchain_platform.composeapp.generated.resources.login
import blockchain_platform.composeapp.generated.resources.logout
import blockchain_platform.composeapp.generated.resources.nav_create_problem
import blockchain_platform.composeapp.generated.resources.nav_problem_list
import blockchain_platform.composeapp.generated.resources.profile
import blockchain_platform.composeapp.generated.resources.settings
import blockchain_platform.composeapp.generated.resources.theme_dark
import blockchain_platform.composeapp.generated.resources.theme_light
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
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(Res.string.app_name),
                    modifier = Modifier.clickable { onHomeClick() }
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            actions = {
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
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isDarkTheme) {
                            stringResource(Res.string.theme_dark)
                        } else {
                            stringResource(Res.string.theme_light)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onThemeChange(it) }
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (isLoggedIn) {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { profileMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = stringResource(Res.string.profile)
                            )
                        }
                        DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.settings)) },
                                onClick = {
                                    profileMenuExpanded = false
                                    onNavigate(Route.Settings)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.logout)) },
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
        )
    }
}

@Composable
private fun NavTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(text)
        }
    } else {
        TextButton(onClick = onClick) {
            Text(text)
        }
    }
}
