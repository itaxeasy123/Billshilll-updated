package com.example.accounting.presentation.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

sealed class AppRoute(val path: String, val title: String) {
    object Dashboard : AppRoute("#dashboard", "Dashboard")
    object DayBook : AppRoute("#daybook", "Day Book")
    object ChartOfAccounts : AppRoute("#chart-of-accounts", "Ledger Accounts")
    data class LedgerStatement(val ledgerId: String) : AppRoute("#statement/$ledgerId", "Ledger Statement")
    object Reports : AppRoute("#reports", "Financial Reports")
    object SettingsAndSync : AppRoute("#settings-sync", "Governance & Outbox Sync")

    companion object {
        fun fromHash(hash: String): AppRoute {
            return when {
                hash.startsWith("#statement/") -> {
                    val id = hash.removePrefix("#statement/")
                    LedgerStatement(id)
                }
                hash == "#daybook" -> DayBook
                hash == "#chart-of-accounts" -> ChartOfAccounts
                hash == "#reports" -> Reports
                hash == "#settings-sync" -> SettingsAndSync
                else -> Dashboard
            }
        }
    }
}

/**
 * HashRouter manages deterministic URL-like hash navigation state, backstack history,
 * and adaptive layout strategies for mobile, foldable, and tablet display form factors.
 */
class HashRouter(initialRoute: AppRoute = AppRoute.Dashboard) {

    private val history = ArrayDeque<AppRoute>()

    private val _currentRoute = MutableStateFlow<AppRoute>(initialRoute)
    val currentRoute: StateFlow<AppRoute> = _currentRoute.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    init {
        history.push(initialRoute)
    }

    fun navigate(destination: AppRoute) {
        if (_currentRoute.value != destination) {
            history.push(destination)
            _currentRoute.value = destination
            _canGoBack.value = history.size > 1
        }
    }

    fun goBack(): Boolean {
        return if (history.size > 1) {
            history.pop() // Pop current
            val previous = history.peek() ?: AppRoute.Dashboard
            _currentRoute.value = previous
            _canGoBack.value = history.size > 1
            true
        } else {
            false
        }
    }

    fun replace(destination: AppRoute) {
        if (history.isNotEmpty()) {
            history.pop()
        }
        history.push(destination)
        _currentRoute.value = destination
        _canGoBack.value = history.size > 1
    }

    fun currentHash(): String = _currentRoute.value.path
}

enum class AdaptiveNavigationType {
    BOTTOM_NAVIGATION_BAR,
    NAVIGATION_RAIL,
    PERMANENT_NAVIGATION_DRAWER
}

/**
 * Computes optimal navigation layout based on Material 3 WindowWidthSizeClass
 */
fun getAdaptiveNavigationType(widthSizeClass: WindowWidthSizeClass): AdaptiveNavigationType {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> AdaptiveNavigationType.BOTTOM_NAVIGATION_BAR
        WindowWidthSizeClass.Medium -> AdaptiveNavigationType.NAVIGATION_RAIL
        WindowWidthSizeClass.Expanded -> AdaptiveNavigationType.NAVIGATION_RAIL
        else -> AdaptiveNavigationType.BOTTOM_NAVIGATION_BAR
    }
}
