package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.ui.components.*

@Composable
fun AdminSettingsScreen(
    onBack: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSync: () -> Unit,
) {
    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Ajustes", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Ajustes da operação",
                    "Somente configurações que podem ser necessárias no dia a dia.",
                )
            }
            item {
                PremiumMenuRow(
                    "Aparelho da portaria",
                    "Use apenas para configurar ou trocar o celular da portaria",
                    Icons.Rounded.PhoneAndroid,
                    onOpenDevice,
                )
            }
            item {
                PremiumMenuRow(
                    "Itens arquivados",
                    "Consulte e restaure cadastros antigos",
                    Icons.Rounded.Archive,
                    onOpenArchived,
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
