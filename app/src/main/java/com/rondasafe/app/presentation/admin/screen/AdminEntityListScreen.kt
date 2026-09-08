package com.rondasafe.app.presentation.admin.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.core.result.UiState
import com.rondasafe.app.ui.components.PremiumTopBar
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeUi

@Composable
fun <T> AdminEntityListScreen(
    title: String,
    state: UiState<List<T>>,
    archived: Boolean,
    key: (T) -> Any,
    onBack: () -> Unit,
    onCreate: (() -> Unit)? = null,
    onArchive: (T) -> Unit = {},
    onRestore: (T) -> Unit = {},
    row: @Composable (item: T, archived: Boolean, onArchive: () -> Unit, onRestore: () -> Unit) -> Unit,
) {
    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar(title, onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onCreate != null && !archived) {
                item {
                    Button(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Novo") }
                }
            }

            when (state) {
                UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is UiState.Error -> item { Text(state.message, color = MaterialTheme.colorScheme.error) }
                is UiState.Success -> {
                    items(state.data, key = { key(it) }) { item ->
                        row(
                            item,
                            archived,
                            { onArchive(item) },
                            { onRestore(item) },
                        )
                    }
                }
            }
        }
    }
}
