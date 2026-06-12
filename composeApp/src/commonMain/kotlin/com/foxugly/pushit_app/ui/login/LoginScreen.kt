package com.foxugly.pushit_app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foxugly.pushit_app.data.repository.AuthRepository
import com.foxugly.pushit_app.ui.components.ErrorBanner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import pushit_app.composeapp.generated.resources.Res
import pushit_app.composeapp.generated.resources.foxugly_logo

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "PushIT", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        error?.let {
            ErrorBanner(it)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
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
                        onFailure = { error = it.message ?: "Login failed" },
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
                Text("Login")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Registration is web-only (the sign-up form is captcha-protected, which a
        // native screen can't satisfy). New users create an account on the dashboard.
        Text(
            text = "New here? Create an account at pushit.foxugly.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

        // "by [logo] Foxugly" credit pinned to the bottom.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "by ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Image(
                painter = painterResource(Res.drawable.foxugly_logo),
                contentDescription = null,
                modifier = Modifier.height(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
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
