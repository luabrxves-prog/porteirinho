package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.ui.components.*

@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSync: () -> Unit,
) {
    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Configurações", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Configurações e manutenção",
                    "O responsável pelas rondas é definido automaticamente pelo porteiro que estiver com o plantão aberto no aparelho.",
                )
            }
            item {
                PremiumMenuRow(
                    "Configurar aparelho",
                    "Vincule este celular à portaria",
                    Icons.Rounded.PhoneAndroid,
                    onOpenDevice,
                )
            }
            item {
                PremiumMenuRow(
                    "Arquivados",
                    "Consulte cadastros extras arquivados",
                    Icons.Rounded.Archive,
                    onOpenArchived,
                )
            }
            item {
                PremiumMenuRow(
                    "Diagnóstico de sincronização",
                    "Abra somente se houver problema de envio dos registros",
                    Icons.Rounded.Sync,
                    onOpenSync,
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
