package com.secretgrave.privacy.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretgrave.privacy.core.crypto.HashUtils
import com.secretgrave.privacy.core.database.SecretDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.content.Context
import android.content.SharedPreferences

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val secretDatabase: SecretDatabase,
    private val hashUtils: HashUtils,
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_prefs",
        Context.MODE_PRIVATE
    )

    private val _authResult = MutableSharedFlow<AuthResult>()
    val authResult: SharedFlow<AuthResult> = _authResult.asSharedFlow()

    private val _lockoutTimeRemaining = MutableStateFlow(0L)
    val lockoutTimeRemaining: StateFlow<Long> = _lockoutTimeRemaining.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _isLockedOut = MutableStateFlow(false)
    val isLockedOut: StateFlow<Boolean> = _isLockedOut.asStateFlow()

    init {
        checkLockoutStatus()
    }

    fun validatePin(pin: String) {
        viewModelScope.launch {
            if (_isLockedOut.value) {
                _authResult.emit(AuthResult.LockedOut)
                return@launch
            }

            try {
                val pinHash = hashUtils.sha256(pin)
                val authConfig = withContext(Dispatchers.IO) {
                    secretDatabase.authConfigDao().getLatest()
                }

                if (authConfig == null) {
                    _authResult.emit(AuthResult.Error("Auth config not found"))
                    return@launch
                }

                when {
                    pinHash == authConfig.pinHash -> {
                        resetFailedAttempts()
                        _authResult.emit(AuthResult.RealVault)
                    }
                    pin == authConfig.fakePin -> {
                        resetFailedAttempts()
                        _authResult.emit(AuthResult.FakeVault)
                    }
                    else -> {
                        handleWrongPin(authConfig.maxAttempts, authConfig.attemptLockoutDurationMs)
                        _authResult.emit(AuthResult.WrongPin)
                    }
                }
            } catch (e: Exception) {
                _authResult.emit(AuthResult.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun handleWrongPin(maxAttempts: Int, lockoutDurationMs: Long) {
        val currentAttempts = _failedAttempts.value + 1
        _failedAttempts.value = currentAttempts

        withContext(Dispatchers.IO) {
            prefs.edit().putInt("failed_attempts", currentAttempts).apply()
        }

        if (currentAttempts >= maxAttempts) {
            lockVault(lockoutDurationMs)
        }
    }

    private suspend fun lockVault(lockoutDurationMs: Long) {
        _isLockedOut.value = true
        val lockoutUntil = System.currentTimeMillis() + lockoutDurationMs
        withContext(Dispatchers.IO) {
            prefs.edit().putLong("lockout_until", lockoutUntil).apply()
        }
        startLockoutCountdown(lockoutDurationMs)
    }

    private fun startLockoutCountdown(lockoutDurationMs: Long) {
        viewModelScope.launch {
            var remainingTime = lockoutDurationMs
            while (remainingTime > 0 && _isLockedOut.value) {
                _lockoutTimeRemaining.value = remainingTime / 1000
                kotlinx.coroutines.delay(1000)
                remainingTime -= 1000
            }
            if (_isLockedOut.value) {
                _isLockedOut.value = false
                _lockoutTimeRemaining.value = 0L
                resetFailedAttempts()
                withContext(Dispatchers.IO) {
                    prefs.edit().remove("lockout_until").apply()
                }
            }
        }
    }

    private suspend fun resetFailedAttempts() {
        _failedAttempts.value = 0
        withContext(Dispatchers.IO) {
            prefs.edit().putInt("failed_attempts", 0).apply()
        }
    }

    private fun checkLockoutStatus() {
        val lockoutUntil = prefs.getLong("lockout_until", 0L)
        val currentTime = System.currentTimeMillis()
        val failedAttempts = prefs.getInt("failed_attempts", 0)

        _failedAttempts.value = failedAttempts

        if (lockoutUntil > currentTime) {
            _isLockedOut.value = true
            val remainingTime = lockoutUntil - currentTime
            startLockoutCountdown(remainingTime)
        } else if (lockoutUntil > 0) {
            viewModelScope.launch {
                resetFailedAttempts()
            }
        }
    }
}

sealed class AuthResult {
    object RealVault : AuthResult()
    object FakeVault : AuthResult()
    object WrongPin : AuthResult()
    object LockedOut : AuthResult()
    data class Error(val message: String) : AuthResult()
}
