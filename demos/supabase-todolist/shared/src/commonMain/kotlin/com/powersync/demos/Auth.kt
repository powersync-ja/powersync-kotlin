package com.powersync.demos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthOptions(
    /**
     * Whether the auth view mode is responsible for connecting to PowerSync.
     * This is a simplification we use in the default example. When starting the
     * androidBackgroundSync app, this is false because we're connecting from a
     * foreground service.
     */
    val connectFromViewModel: Boolean
)

sealed class AuthState {
    data object SignedOut : AuthState()

    data object SignedIn : AuthState()
}

internal class AuthViewModel(
    private val supabase: SupabaseConnector,
    private val db: PowerSyncDatabase,
    private val navController: NavController,
    authOptions: AuthOptions,
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    init {
        if (authOptions.connectFromViewModel) {
            viewModelScope.launch {
                supabase.sessionStatus.collect {
                    when (it) {
                        is SessionStatus.Authenticated -> {
                            db.connect(supabase)
                        }
                        is SessionStatus.NotAuthenticated -> {
                            db.disconnectAndClear()
                        }
                        else -> {
                            // Ignore
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            supabase.sessionStatus.collect {
                when (it) {
                    is SessionStatus.Authenticated -> {
                        _authState.value = AuthState.SignedIn
                        _userId.value = it.session.user?.id
                        navController.navigate(Screen.Home)
                    }

                    SessionStatus.Initializing -> Logger.e("Loading from storage")
                    is SessionStatus.RefreshFailure -> {
                        when (it.cause) {
                            is RefreshFailureCause.NetworkError -> Logger.e("Network error occurred")
                            is RefreshFailureCause.InternalServerError -> Logger.e("Internal server error occurred")
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _authState.value = AuthState.SignedOut
                        navController.navigate(Screen.SignIn)
                    }
                }
            }
        }
    }

    suspend fun signIn(
        email: String,
        password: String,
    ) {
        supabase.login(email, password)
        _authState.value = AuthState.SignedIn
    }

    suspend fun signUp(
        email: String,
        password: String,
    ) {
        supabase.signUp(email, password)
        _authState.value = AuthState.SignedIn
    }

    suspend fun signOut() {
        try {
            supabase.signOut()
        } catch (e: Exception) {
            Logger.e("Error signing out: $e")
        } finally {
            _authState.value = AuthState.SignedOut
        }
    }
}
