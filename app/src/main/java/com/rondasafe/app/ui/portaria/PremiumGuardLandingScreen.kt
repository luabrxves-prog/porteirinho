package com.rondasafe.app.ui.portaria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeMark
import kotlinx.coroutines.launch

@Composable
fun PremiumGuardLandingScreen(
    enabled: Boolean,
    onGuardSelected: (PortariaGuardDto) -> Unit,
    onAdmin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<PortariaGuardDto>>(emptyList()) }
    var loading by remember { mutableStateOf(enabled) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        if (!enabled) return
        scope.launch {
            loading = true
            error = null
            runCatching { PortariaRepository.listGuards() }
                .onSuccess { guards = it }
                .onFailure { error = it.message ?: "Nao foi possivel carregar os porteiros." }
            loading = false
        }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            PortariaRepository.scheduleOfflineSync()
            load()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RondaSafeColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = RondaSafeColors.Navy,
                    shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RondaSafeMark(modifier = Modifier.size(48.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    "RondaSafe",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                                Text(
                                    "Portaria",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.66f),
                                )
                            }
                            IconButton(onClick = onAdmin) {
                                Icon(
                                    Icons.Rounded.AdminPanelSettings,
                                    contentDescription = "Administrador",
                                    tint = Color.White,
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Quem esta iniciando o turno?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                "Toque no seu perfil para acessar as rondas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }

            if (!enabled) {
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, RondaSafeColors.Border),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Aparelho ainda nao configurado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RondaSafeColors.Navy,
                            )
                            Text(
                                "Entre como administrador e configure este celular como portaria.",
                                color = RondaSafeColors.Muted,
                            )
                            Button(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.AdminPanelSettings, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Abrir administrador")
                            }
                        }
                    }
                }
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                message,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            IconButton(onClick = ::load) {
                                Icon(Icons.Rounded.Refresh, "Tentar novamente")
                            }
                        }
                    }
                }
            }

            items(guards, key = { it.id }) { guard ->
                Card(
                    onClick = { onGuardSelected(guard) },
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!guard.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = guard.photoUrl,
                                contentDescription = "Foto de ${guard.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = RondaSafeColors.BlueSoft,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        guard.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = RondaSafeColors.Navy,
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                guard.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = RondaSafeColors.Navy,
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (guard.pinState == "TEMPORARY") {
                                    Color(0xFFFFF4E4)
                                } else {
                                    RondaSafeColors.GreenSoft
                                },
                            ) {
                                Text(
                                    if (guard.pinState == "TEMPORARY") "Primeiro acesso" else "Pronto para entrar",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (guard.pinState == "TEMPORARY") {
                                        Color(0xFF9B5B00)
                                    } else {
                                        RondaSafeColors.Green
                                    },
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = RondaSafeColors.BlueSoft,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = RondaSafeColors.Navy,
                                )
                            }
                        }
                    }
                }
            }

            if (!loading && enabled && guards.isEmpty() && error == null) {
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, RondaSafeColors.Border),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = RondaSafeColors.BlueSoft,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Badge, null, tint = RondaSafeColors.Navy)
                                }
                            }
                            Text(
                                "Nenhum porteiro ativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RondaSafeColors.Navy,
                            )
                            Text(
                                "Cadastre ou ative um perfil no painel administrativo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
        }
    }
}
