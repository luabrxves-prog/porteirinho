package com.rondasafe.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeMark
import com.rondasafe.app.ui.components.userFriendlyError
import kotlinx.coroutines.launch

@Composable
fun PremiumAdminLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(RondaSafeColors.Background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF073B60), RondaSafeColors.Navy, RondaSafeColors.NavyDark),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "ADMINISTRAÇÃO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = .72f),
                )
                Spacer(Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RondaSafeMark(modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "RondaSafe",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            "Gestão segura do condomínio",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = .70f),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Bem-vindo de volta",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Acesse o painel para acompanhar e configurar as rondas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = .76f),
                )
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = Color.White,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Entrar na sua conta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = RondaSafeColors.Navy,
                    )
                    Text(
                        "Use o e-mail e a senha do administrador.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RondaSafeColors.Muted,
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("E-mail") },
                        leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Senha") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    )

                    error?.let {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(13.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                AuthRepository.signIn(email.trim(), password)
                                    .onSuccess { onLoginSuccess() }
                                    .onFailure {
                                        error = if (it.message.orEmpty().contains("invalid", true)) {
                                            "E-mail ou senha incorretos."
                                        } else {
                                            userFriendlyError(it, "Não foi possível entrar agora. Tente novamente.")
                                        }
                                    }
                                loading = false
                            }
                        },
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RondaSafeColors.Navy),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (loading) "Entrando..." else "Entrar", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.weight(1f))
                    Text(
                        "Acesso exclusivo para administradores autorizados.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RondaSafeColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
