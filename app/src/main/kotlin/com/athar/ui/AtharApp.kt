package com.athar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.athar.core.designsystem.component.AtharBottomBar
import com.athar.core.designsystem.component.AtharBottomBarItem
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.display.LocalDisplayCurrency
import com.athar.core.designsystem.display.LocalHijriEnabled
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.feature.plan.PlanScreen
import com.athar.feature.settings.ActivityLogScreen
import com.athar.feature.settings.CategoriesScreen
import com.athar.feature.settings.RecurringRulesScreen
import com.athar.feature.settings.SettingsScreen
import com.athar.feature.settings.SmsAuditScreen
import com.athar.feature.settings.UserTemplatesScreen
import com.athar.feature.today.TodayScreen
import com.athar.feature.trends.TrendsScreen
import com.athar.ui.onboarding.OnboardingScreen
import com.athar.ui.onboarding.OnboardingViewModel

@Composable
fun AtharApp() {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboarded by onboardingViewModel.onboardingComplete.collectAsStateWithLifecycle()

    val appPrefs: AppPrefsViewModel = hiltViewModel()
    val hijriEnabled by appPrefs.hijriEnabled.collectAsStateWithLifecycle()
    val displayCurrency by appPrefs.displayCurrency.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val current by navController.currentBackStackEntryAsState()
    val routeName = current?.destination?.route.orEmpty()
    val isOnboarding = routeName.endsWith("Onboarding")
    val isSettingsArea = routeName.endsWith("Settings") ||
        routeName.endsWith("Categories") ||
        routeName.endsWith("SmsAudit") ||
        routeName.endsWith("ActivityLog") ||
        routeName.endsWith("UserTemplates") ||
        routeName.endsWith("RecurringRules")
    val theme = AtharTheme

    CompositionLocalProvider(
        LocalHijriEnabled provides hijriEnabled,
        LocalDisplayCurrency provides displayCurrency,
    ) {
        Scaffold(
            topBar = {
                if (!isOnboarding && !isSettingsArea) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = theme.spacing.s, vertical = theme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SettingsGear(onClick = { navController.navigate(Routes.Settings) })
                    }
                }
            },
            bottomBar = {
                if (!isOnboarding) {
                    AtharBottomBar(
                        items = TopLevelTabs,
                        selectedKey = current.selectedTabKey(),
                        onSelect = { key ->
                            val route: Any = when (key) {
                                "today" -> Routes.Today
                                "trends" -> Routes.Trends
                                "plan" -> Routes.Plan
                                else -> Routes.Today
                            }
                            navController.navigate(route) {
                                popUpTo(Routes.Today) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding: PaddingValues ->
            NavHost(
                navController = navController,
                startDestination = if (onboarded) Routes.Today else Routes.Onboarding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                composable<Routes.Onboarding> {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(Routes.Today) {
                                popUpTo(Routes.Onboarding) { inclusive = true }
                            }
                        },
                    )
                }
                composable<Routes.Today> { TodayScreen() }
                composable<Routes.Trends> { TrendsScreen() }
                composable<Routes.Plan> { PlanScreen() }
                composable<Routes.Settings> {
                    SettingsScreen(
                        onOpenCategories = { navController.navigate(Routes.Categories) },
                        onOpenSmsAudit = { navController.navigate(Routes.SmsAudit) },
                        onOpenActivityLog = { navController.navigate(Routes.ActivityLog) },
                        onOpenUserTemplates = { navController.navigate(Routes.UserTemplates) },
                        onOpenRecurringRules = { navController.navigate(Routes.RecurringRules) },
                    )
                }
                composable<Routes.UserTemplates> {
                    UserTemplatesScreen(onBack = { navController.popBackStack() })
                }
                composable<Routes.RecurringRules> {
                    RecurringRulesScreen(onBack = { navController.popBackStack() })
                }
                composable<Routes.Categories> {
                    CategoriesScreen(onBack = { navController.popBackStack() })
                }
                composable<Routes.SmsAudit> {
                    SmsAuditScreen(onBack = { navController.popBackStack() })
                }
                composable<Routes.ActivityLog> {
                    ActivityLogScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun SettingsGear(onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.parchment)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = "⋯", style = theme.typography.title, color = theme.colors.muted)
    }
}

private val TopLevelTabs: List<AtharBottomBarItem> = listOf(
    AtharBottomBarItem(key = "today", labelEn = "Today", labelAr = "اليوم"),
    AtharBottomBarItem(key = "trends", labelEn = "Trends", labelAr = "النمط"),
    AtharBottomBarItem(key = "plan", labelEn = "Plan", labelAr = "الخطة"),
)

private fun NavBackStackEntry?.selectedTabKey(): String {
    val route = this?.destination?.route.orEmpty()
    return when {
        route.endsWith("Trends") -> "trends"
        route.endsWith("Plan") -> "plan"
        else -> "today"
    }
}
