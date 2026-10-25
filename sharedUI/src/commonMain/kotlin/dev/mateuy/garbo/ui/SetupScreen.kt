package dev.mateuy.garbo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mateuy.garbo.vault.PickedFolder
import dev.mateuy.garbo.vault.VaultError
import dev.mateuy.garbo.vault.toPickedFolder
import garbo.sharedui.generated.resources.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Shown while no vault is open: create one in an empty folder, or open an existing one. */
@Composable
internal fun SetupScreen(error: VaultError?, busy: Boolean) {
    val session = LocalAppGraph.current.session
    val scope = rememberCoroutineScope()
    // Errors of the last attempt come from the vault state; only picker failures are local.
    var pickError by remember { mutableStateOf<Throwable?>(null) }
    var openInstead by remember { mutableStateOf<PickedFolder?>(null) }
    val shownError = pickError ?: error?.takeUnless { it is VaultError.AlreadyAVault }

    fun pick(folder: PlatformFile?, create: Boolean) {
        folder ?: return
        scope.launch {
            pickError = null
            val picked = try {
                folder.toPickedFolder()
            } catch (e: Exception) {
                pickError = VaultError.Unreachable(e)
                return@launch
            }
            val result = if (create) session.create(picked) else session.open(picked)
            if (result is VaultError.AlreadyAVault) openInstead = picked
        }
    }

    val createLauncher = rememberDirectoryPickerLauncher { pick(it, create = true) }
    val openLauncher = rememberDirectoryPickerLauncher { pick(it, create = false) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.setup_intro),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { createLauncher.launch() }, modifier = Modifier.widthIn(min = 220.dp)) {
                Text(stringResource(Res.string.setup_create))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { openLauncher.launch() }, modifier = Modifier.widthIn(min = 220.dp)) {
                Text(stringResource(Res.string.setup_open))
            }
        }
        shownError?.let {
            Spacer(Modifier.height(24.dp))
            Text(
                errorMessage(it),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }

    openInstead?.let { folder ->
        AlertDialog(
            onDismissRequest = { openInstead = null },
            title = { Text(stringResource(Res.string.setup_open_instead_title)) },
            text = { Text(stringResource(Res.string.setup_open_instead_text)) },
            confirmButton = {
                TextButton(onClick = {
                    openInstead = null
                    scope.launch { session.open(folder) }
                }) { Text(stringResource(Res.string.setup_open_instead)) }
            },
            dismissButton = {
                TextButton(onClick = { openInstead = null }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
}
