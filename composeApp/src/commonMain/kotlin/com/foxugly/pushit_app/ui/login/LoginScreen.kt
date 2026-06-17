package com.foxugly.pushit_app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import com.foxugly.pushit_app.ui.i18n.errorText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pushit_app.composeapp.generated.resources.Res
import pushit_app.composeapp.generated.resources.foxugly_logo
import pushit_app.composeapp.generated.resources.pushit_logo

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    apiBaseUrl: String = "",
    allowBackendSwitch: Boolean = false,
    prodApiBaseUrl: String = "",
    localApiBaseUrl: String = "",
    onSwitchBackend: (String) -> Unit = {},
) {
    // Which backend this build talks to — handy to tell prod from a local dev server.
    val apiHost = apiBaseUrl.substringAfter("://").substringBefore("/").ifBlank { apiBaseUrl }
    val isProd = apiHost.contains("foxugly.com")
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text(strings.email) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
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
                    authRepository.login(email, password).fold(
                        onSuccess = { onLoginSuccess() },
                        // A failed login is almost always bad credentials; show a
                        // friendly message instead of the raw API error. Keep the
                        // server-error and transport (offline/timeout) cases distinct.
                        onFailure = { throwable ->
                            error = when {
                                throwable is ApiException && throwable.statusCode in 500..599 ->
                                    strings.serverError
                                throwable is ApiException -> strings.invalidCredentials
                                else -> strings.errorText(throwable, strings.loginFailed)
                            }
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

        if (apiHost.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "● " + (if (isProd) strings.backendProd else strings.backendLocal) + " · " + apiHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isProd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                // Debug-only runtime backend switch: flip between prod and the local
                // dev server. Switching clears tokens and returns to login.
                if (allowBackendSwitch) {
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = !isProd,
                        onCheckedChange = { toLocal ->
                            onSwitchBackend(if (toLocal) localApiBaseUrl else prodApiBaseUrl)
                        },
                    )
                }
            }
        }
    }

        // "by [logo] Foxugly" credit pinned to the bottom.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
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
