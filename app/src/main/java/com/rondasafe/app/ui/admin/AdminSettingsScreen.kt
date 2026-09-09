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
    onOpenAssignments: () -> Unit,
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
                    "Ajustes",
                    "Itens arquivados e aparelho da portaria.",
                )
            }
            item {
                PremiumMenuRow(
                    "Itens arquivados",
                    "Consulte e restaure cadastros arquivados anteriormente",
                    Icons.Rounded.Archive,
                    onOpenArchived,
                )
            }
            item {
                PremiumMenuRow(
                    "Aparelho da portaria",
                    "Configure ou troque o celular usado na portaria",
                    Icons.Rounded.PhoneAndroid,
                    onOpenDevice,
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
