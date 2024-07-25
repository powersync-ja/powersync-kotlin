package com.powersync.demos

import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

sealed class AuthState {
    data object SignedOut: AuthState()
    data object SignedIn: AuthState()
}

internal class AuthViewModel(
    private val supabase: SupabaseConnector,
    private val db: PowerSyncDatabase
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState


    private fun connectDatabase() {
        runBlocking {
            db.connect(supabase)
        }
    }

    private fun disconnectDatabase() {
        runBlocking {
            db.disconnect()
        }
    }

    init {
        checkSession()
    }

    private fun checkSession() {
        runBlocking {
            val session = supabase.session()
            if (session != null) {
                _authState.value = AuthState.SignedIn
            } else {
                _authState.value = AuthState.SignedOut
            }
        }
    }

    fun signIn(email: String, password: String) {
        runBlocking {
            supabase.login(email, password)
            connectDatabase()
            _authState.value = AuthState.SignedIn
        }
    }

    fun signUp(email: String, password: String) {
        runBlocking {
            supabase.signUp(email, password)
            connectDatabase()
            _authState.value = AuthState.SignedIn
        }
    }

    fun signOut() {
        runBlocking {
            supabase.signOut()
            disconnectDatabase()
            _authState.value = AuthState.SignedOut
        }
    }
}
