package com.secretgrave.privacy.features.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import android.widget.Button
import com.secretgrave.privacy.databinding.FragmentLockScreenBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@AndroidEntryPoint
class LockScreenFragment : Fragment() {

    private var _binding: FragmentLockScreenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    private var enteredPin = ""
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLockScreenBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBiometric()
        setupUI()
        observeViewModel()
    }

    private fun setupBiometric() {
        val executor: Executor = ContextCompat.getMainExecutor(requireContext())
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (enteredPin.isNotEmpty()) {
                    viewModel.validatePin(enteredPin)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(context, "Biometric error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupUI() {
        binding.btnConfirm.setOnClickListener { onConfirmClicked() }
        binding.btnClear.setOnClickListener { onClearClicked() }
        binding.btnBiometric.setOnClickListener { onBiometricClicked() }
        binding.btnDelete.setOnClickListener { onDeleteClicked() }

        for (i in 0..9) {
            val buttonId = resources.getIdentifier("btn_$i", "id", requireContext().packageName)
            view?.findViewById<Button>(buttonId)?.setOnClickListener { onNumberClicked(i.toString()) }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.authResult.collect { result ->
                when (result) {
                    is AuthResult.RealVault -> {
                        Toast.makeText(context, "Vault unlocked", Toast.LENGTH_SHORT).show()
                    }
                    is AuthResult.FakeVault -> {
                        Toast.makeText(context, "Decoy vault opened", Toast.LENGTH_SHORT).show()
                    }
                    is AuthResult.WrongPin -> {
                        shakeAnimation()
                        enteredPin = ""
                        updatePinDisplay()
                        Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    }
                    is AuthResult.LockedOut -> {
                        binding.btnConfirm.isEnabled = false
                        Toast.makeText(context, "Vault locked", Toast.LENGTH_LONG).show()
                    }
                    is AuthResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLockedOut.collect { isLocked ->
                binding.btnConfirm.isEnabled = !isLocked
            }
        }

        lifecycleScope.launch {
            viewModel.lockoutTimeRemaining.collect { seconds ->
                if (seconds > 0) {
                    binding.tvLockoutMessage.visibility = View.VISIBLE
                    binding.tvLockoutMessage.text = "Locked for ${seconds}s"
                } else {
                    binding.tvLockoutMessage.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.failedAttempts.collect { attempts ->
                binding.tvAttempts.text = "Failed: $attempts/5"
            }
        }
    }

    private fun onNumberClicked(digit: String) {
        if (enteredPin.length < 6) {
            enteredPin += digit
            updatePinDisplay()
        }
    }

    private fun onClearClicked() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updatePinDisplay()
        }
    }

    private fun onDeleteClicked() {
        enteredPin = ""
        updatePinDisplay()
    }

    private fun updatePinDisplay() {
        val dots = "●".repeat(enteredPin.length) + "○".repeat(6 - enteredPin.length)
        binding.tvPinDisplay.text = dots
    }

    private fun onConfirmClicked() {
        if (enteredPin.length != 6) {
            Toast.makeText(context, "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.validatePin(enteredPin)
    }

    private fun onBiometricClicked() {
        val canAuthenticate = BiometricManager.from(requireContext())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Use fingerprint to unlock")
                .setNegativeButtonText("Cancel")
                .build()
            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(context, "Biometric not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shakeAnimation() {
        val shake = TranslateAnimation(0f, 25f, 0f, 0f)
        shake.duration = 100
        shake.repeatCount = 5
        binding.tvPinDisplay.startAnimation(shake)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
