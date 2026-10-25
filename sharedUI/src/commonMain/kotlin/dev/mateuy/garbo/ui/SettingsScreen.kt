package dev.mateuy.garbo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mateuy.garbo.vault.VaultState
import garbo.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(state: VaultState.Open, navigator: Navigator) {
    val session = LocalAppGraph.current.session
    val scope = rememberCoroutineScope()
    var confirmClose by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = { BackButton(navigator::back) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.settings_vault_location)) },
                supportingContent = { SelectionContainer { Text(state.location) } },
            )
            OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.settings_close_vault))
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(Res.string.settings_close_vault)) },
            text = { Text(stringResource(Res.string.settings_close_vault_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    scope.launch { session.close() }
                }) { Text(stringResource(Res.string.settings_close_vault)) }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
