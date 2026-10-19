package com.foxugly.pushit_app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.api.ApiException
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import com.foxugly.pushit_app.ui.components.PasswordHiddenIcon
import com.foxugly.pushit_app.ui.components.PasswordVisibleIcon
import com.foxugly.pushit_app.ui.i18n.LocalStrings
import com.foxugly.pushit_app.ui.i18n.loginErrorText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pushit_app.composeapp.generated.resources.Res
import pushit_app.composeapp.generated.resources.foxugly_logo
import pushit_app.composeapp.generated.resources.pushit_logo

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Set after a 403 `email_not_verified`: offers the "resend confirmation" action.
    var emailNotVerified by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    // Scrollable + inset-aware: with the keyboard open, a large font or a small
    // screen, the fields, the button and the credit stay reachable. The column is
    // at least the viewport tall, so on a roomy screen the form stays centred and
    // the credit sits at the bottom (SpaceBetween), as before.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
    val viewportHeight = maxHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewportHeight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
    Spacer(Modifier.height(0.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.pushit_logo),
            contentDescription = null,
            modifier = Modifier.height(96.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "PushIT",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))

        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(16.dp))
        }
        if (emailNotVerified) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isResending = true
                        authRepository.resendConfirmationEmail(email).fold(
                            onSuccess = {
                                error = null
                                info = strings.confirmationResent
                            },
                            onFailure = { throwable -> error = strings.loginErrorText(throwable) },
                        )
                        isResending = false
                    }
                },
                enabled = !isResending && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.resendConfirmation)
            }
            Spacer(Modifier.height(16.dp))
        }
        info?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null; info = null; emailNotVerified = false },
            label = { Text(strings.email) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null; info = null },
            label = { Text(strings.password) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) PasswordHiddenIcon else PasswordVisibleIcon,
                        contentDescription = if (passwordVisible) strings.hidePassword else strings.showPassword,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    error = null
                    info = null
                    emailNotVerified = false
                    authRepository.login(email, password).fold(
                        onSuccess = { onLoginSuccess() },
                        onFailure = { throwable ->
                            emailNotVerified = throwable is ApiException &&
                                throwable.errorCode == AuthRepository.ERROR_EMAIL_NOT_VERIFIED
                            error = strings.loginErrorText(throwable)
                        },
                    )
                    isLoading = false
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(strings.login)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Registration is web-only (the sign-up form is captcha-protected, which a
        // native screen can't satisfy). New users create an account on the dashboard.
        Text(
            text = strings.registerHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

        // "by [logo] Foxugly" credit at the bottom.
        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.credit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Original Foxugly navy (#1b1a30, baked into the vector) — the app is
            // light-only today so it reads fine; revisit if dark mode is added.
            Image(
                painter = painterResource(Res.drawable.foxugly_logo),
                contentDescription = null,
                modifier = Modifier.height(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Foxugly",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
}
