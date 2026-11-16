package com.onemanarmy.airalerts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onemanarmy.airalerts.data.local.AppPreferencesRepository
import com.onemanarmy.airalerts.data.repository.AlertMonitoringRepository
import com.onemanarmy.airalerts.platform.BackgroundServiceController
import com.onemanarmy.airalerts.resources.Res
import com.onemanarmy.airalerts.resources.tab_map
import com.onemanarmy.airalerts.resources.tab_points
import com.onemanarmy.airalerts.resources.tab_settings
import com.onemanarmy.airalerts.ui.map.MapTabScreen
import com.onemanarmy.airalerts.ui.onboarding.OnboardingScreen
import com.onemanarmy.airalerts.ui.points.PointsTabScreen
import com.onemanarmy.airalerts.ui.settings.SettingsTabScreen
import com.onemanarmy.airalerts.ui.theme.AirAlertsTheme
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed class MainTab(
    val route: String,
    val labelRes: StringResource,
    val icon: ImageVector
) {
    data object Map : MainTab("map", Res.string.tab_map, Icons.Default.Map)
    data object Points : MainTab("points", Res.string.tab_points, Icons.Default.LocationOn)
    data object Settings : MainTab("settings", Res.string.tab_settings, Icons.Default.Settings)

    companion object {
        val entries = listOf(Map, Points, Settings)
    }
}

@Composable
fun App() {
    val prefsRepo: AppPreferencesRepository = koinInject()
    val serviceController: BackgroundServiceController = koinInject()
    val monitoringRepo: AlertMonitoringRepository = koinInject()

    val onboardingFlow = remember(prefsRepo) {
        prefsRepo.settingsFlow.map { it.isOnboardingCompleted as Boolean? }
    }
    val isOnboardingCompleted by onboardingFlow.collectAsStateWithLifecycle(initialValue = null)

    AirAlertsTheme(darkTheme = true) {
        when (isOnboardingCompleted) {
            null -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            false -> {
                OnboardingScreen()
            }

            true -> {
                LaunchedEffect(Unit) {
                    serviceController.startMonitoringService()
                    monitoringRepo.refreshRegionsIfNeeded()
                }
                MainAppScaffold()
            }
        }
    }
}

@Composable
private fun MainAppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navigateToTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth > maxHeight && maxWidth >= 500.dp

        if (useNavigationRail) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.zIndex(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Vertical + WindowInsetsSides.Start
                        )
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        MainTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    VerticalDivider(
                        modifier = Modifier.zIndex(1f),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Vertical + WindowInsetsSides.End
                                )
                            )
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = MainTab.Map.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(MainTab.Map.route) { MapTabScreen() }
                            composable(MainTab.Points.route) { PointsTabScreen() }
                            composable(MainTab.Settings.route) { SettingsTabScreen() }
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp
                        ) {
                            MainTab.entries.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab) },
                                    icon = {
                                        Icon(
                                            tab.icon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = MainTab.Map.route,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    composable(MainTab.Map.route) { MapTabScreen() }
                    composable(MainTab.Points.route) { PointsTabScreen() }
                    composable(MainTab.Settings.route) { SettingsTabScreen() }
                }
            }
        }
    }
}
