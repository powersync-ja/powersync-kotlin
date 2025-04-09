package com.powersync.demos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen {
    data object Home : Screen()
    data object SignIn : Screen()
    data object SignUp : Screen()
    data object Todos : Screen()
    data object Search : Screen()
}

class NavController(initialScreen: Screen) {
    private val backStack = mutableListOf<Screen>()

    private val _currentScreen = MutableStateFlow<Screen>(initialScreen)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    init {
        backStack.add(initialScreen)
    }

    /**
     * Navigates to a new screen, adding it to the top of the back stack.
     * Avoids adding the same screen consecutively.
     */
    fun navigate(screen: Screen) {
        if (screen != backStack.lastOrNull()) {
            backStack.add(screen)
            _currentScreen.value = screen
        }
    }

    /**
     * Navigates back to the previous screen in the stack, if available.
     * Returns true if navigation occurred, false otherwise.
     */
    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeLast()
            _currentScreen.value = backStack.last()
            return true
        }
        return false
    }

    /**
     * Checks if back navigation is possible.
     */
    fun canNavigateBack(): Boolean {
        return backStack.size > 1
    }
}