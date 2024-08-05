package com.powersync.demos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Screen {
    data object Home : Screen()
    data object SignIn : Screen()
    data object SignUp : Screen()
    data object Todos : Screen()
}

internal class NavController(initialScreen: Screen) {
    private val _currentScreen = MutableStateFlow<Screen>(initialScreen)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigate(screen: Screen) {
        _currentScreen.value = screen
    }
}