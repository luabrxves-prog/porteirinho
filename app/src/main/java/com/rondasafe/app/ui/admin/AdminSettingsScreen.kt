package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AssignmentInd
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
                    "Itens usados na implantação ou quando você precisar alterar a operação.",
                )
            }
            item {
                PremiumMenuRow(
                    "Responsáveis por ronda",
                    "Opcional — defina quais porteiros podem realizar cada ronda",
                    Icons.Rounded.AssignmentInd,
                    onOpenAssignments,
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
                    "Consulte e restaure cadastros antigos",
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
