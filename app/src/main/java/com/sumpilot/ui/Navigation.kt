package com.sumpilot.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sumpilot.ui.calculator.CalculatorScreen
import com.sumpilot.ui.history.BadgesScreen
import com.sumpilot.ui.history.HistoryScreen
import com.sumpilot.ui.mission.MissionScreen
import com.sumpilot.ui.mistakes.MistakesScreen
import com.sumpilot.ui.mistakes.RetryScreen
import com.sumpilot.ui.panel.PanelScreen
import com.sumpilot.ui.results.ResultsScreen
import com.sumpilot.ui.results.ReviewScreen
import com.sumpilot.ui.settings.PrivacyScreen
import com.sumpilot.ui.settings.SettingsScreen
import com.sumpilot.ui.theme.LocalReducedMotion

object Routes {
    const val PANEL = "panel"
    const val CALCULATOR = "calculator"
    const val MISSION = "mission/{autoStart}"
    const val RESULTS = "results/{sessionId}"
    const val REVIEW = "review/{sessionId}"
    const val MISTAKES = "mistakes?sessionId={sessionId}"
    const val RETRY = "retry?mistakeId={mistakeId}&sessionId={sessionId}"
    const val HISTORY = "history"
    const val BADGES = "badges"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    /** autoStart = true when the child pressed Start/Resume; false after process recreation. */
    fun mission(autoStart: Boolean) = "mission/$autoStart"
    fun results(sessionId: Long) = "results/$sessionId"
    fun review(sessionId: Long) = "review/$sessionId"
    fun mistakes(sessionId: Long? = null) = "mistakes?sessionId=${sessionId ?: -1L}"
    fun retry(mistakeId: Long? = null, sessionId: Long? = null) =
        "retry?mistakeId=${mistakeId ?: -1L}&sessionId=${sessionId ?: -1L}"
}

private fun NavHostController.backToPanel() {
    if (!popBackStack(Routes.PANEL, inclusive = false)) navigate(Routes.PANEL)
}

@Composable
fun SumPilotNavHost(navController: NavHostController = rememberNavController()) {
    val reduced = LocalReducedMotion.current
    NavHost(
        navController = navController,
        startDestination = Routes.PANEL,
        enterTransition = { if (reduced) EnterTransition.None else fadeIn() },
        exitTransition = { if (reduced) ExitTransition.None else fadeOut() },
        popEnterTransition = { if (reduced) EnterTransition.None else fadeIn() },
        popExitTransition = { if (reduced) ExitTransition.None else fadeOut() },
    ) {
        composable(Routes.PANEL) {
            PanelScreen(
                onOpenCalculator = { navController.navigate(Routes.CALCULATOR) },
                onOpenMission = { navController.navigate(Routes.mission(autoStart = true)) },
                onViewResult = { navController.navigate(Routes.results(it)) },
                onOpenMistakes = { navController.navigate(Routes.mistakes()) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenBadges = { navController.navigate(Routes.BADGES) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CALCULATOR) {
            CalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.MISSION,
            arguments = listOf(navArgument("autoStart") { type = NavType.BoolType; defaultValue = false }),
        ) {
            MissionScreen(
                onBack = { navController.popBackStack() },
                onFinished = { sessionId ->
                    navController.navigate(Routes.results(sessionId)) { popUpTo(Routes.PANEL) }
                },
                onDiscarded = { navController.backToPanel() },
            )
        }
        composable(
            Routes.RESULTS,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            ResultsScreen(
                onBack = { navController.popBackStack() },
                onReview = { navController.navigate(Routes.review(it)) },
                onPracticeMistakes = { navController.navigate(Routes.mistakes(it)) },
                onStartAnother = { navController.navigate(Routes.mission(autoStart = true)) { popUpTo(Routes.PANEL) } },
                onBackToPanel = { navController.backToPanel() },
            )
        }
        composable(
            Routes.REVIEW,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            ReviewScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.MISTAKES,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            MistakesScreen(
                onBack = { navController.popBackStack() },
                onRetry = { mistakeId, sessionId -> navController.navigate(Routes.retry(mistakeId, sessionId)) },
            )
        }
        composable(
            Routes.RETRY,
            arguments = listOf(
                navArgument("mistakeId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("sessionId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            RetryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { navController.navigate(Routes.results(it)) },
            )
        }
        composable(Routes.BADGES) {
            BadgesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
