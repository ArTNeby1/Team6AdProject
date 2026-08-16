package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onCreateAccount: () -> Unit,
    isLoading: Boolean = false,
    serverError: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthPage(
        title = "Welcome back",
        subtitle = "Sign in to continue planning your next story."
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp)
        )
        (error ?: serverError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Button(
            enabled = !isLoading,
            onClick = {
                if (!email.contains("@") || password.length < 6) {
                    error = "Enter a valid email and a password of at least 6 characters."
                } else {
                    error = null
                    onLogin(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Signing in..." else "Sign in", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create an account")
        }
    }
}

@Composable
fun RegisterScreen(
    onRegister: (String, String, String, Int, String) -> Unit,
    onBackToLogin: () -> Unit,
    isLoading: Boolean = false,
    serverError: String? = null
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthPage(
        title = "Create your account",
        subtitle = "Save imported guides and reopen your trips anywhere."
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Display name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("At least 8 characters") },
            shape = RoundedCornerShape(16.dp)
        )
        OutlinedTextField(
            value = age,
            onValueChange = { value ->
                if (value.length <= 3 && value.all(Char::isDigit)) age = value
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp)
        )
        Text("Gender", fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Male", "Female", "Other").forEach { option ->
                FilterChip(
                    selected = gender == option,
                    onClick = { gender = option; error = null },
                    label = { Text(option) }
                )
            }
        }
        (error ?: serverError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Button(
            enabled = !isLoading,
            onClick = {
                val parsedAge = age.toIntOrNull()
                if (name.isBlank() || !email.contains("@") || password.length < 8 || parsedAge !in 1..120) {
                    error = "Complete all fields with a valid email, age and a password of at least 8 characters."
                } else {
                    error = null
                    onRegister(name, email, password, requireNotNull(parsedAge), gender)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Creating account..." else "Create account", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onBackToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to sign in")
        }
    }
}

@Composable
private fun AuthPage(
    title: String,
    subtitle: String,
    fields: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 42.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Loomytrip",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
        Spacer(Modifier.height(34.dp))
        Text(title, fontSize = 31.sp, fontWeight = FontWeight.Bold, lineHeight = 37.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            fields()
        }
    }
}
