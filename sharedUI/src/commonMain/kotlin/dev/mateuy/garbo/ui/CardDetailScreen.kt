package dev.mateuy.garbo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.mateuy.garbo.vault.Card
import dev.mateuy.garbo.vault.PhotoSide
import dev.mateuy.garbo.vault.VaultError
import dev.mateuy.garbo.vault.VaultState
import garbo.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardDetailScreen(state: VaultState.Open, cardId: String, navigator: Navigator) {
    val repository = LocalAppGraph.current.repository
    val scope = rememberCoroutineScope()
    val card = state.snapshot.cards.firstOrNull { it.cardId == cardId }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name.orEmpty()) },
                navigationIcon = { BackButton(navigator::back) },
                actions = {
                    if (card != null) {
                        ActionIcon(Res.drawable.ic_edit, stringResource(Res.string.action_edit)) {
                            navigator.go(Screen.CardEdit(card.cardId, card.ownerId))
                        }
                        ActionIcon(Res.drawable.ic_delete, stringResource(Res.string.action_delete)) { confirmDelete = true }
                    }
                },
            )
        },
    ) { padding ->
        if (card == null) {
            // Deleted or moved away by another app since the list was shown.
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.card_gone))
            }
        } else {
            CardDetails(card, state, Modifier.padding(padding))
        }
    }

    if (confirmDelete && card != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.card_delete_title)) },
            text = { Text(stringResource(Res.string.card_delete_text, card.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            repository.change { deleteCard(card) }
                            navigator.back()
                        } catch (e: VaultError) {
                            error = e
                        }
                    }
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    error?.let { ErrorDialog(it) { error = null } }
}

@Composable
private fun CardDetails(card: Card, state: VaultState.Open, modifier: Modifier) {
    val owner = state.snapshot.owner(card.ownerId)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (owner != null) {
                ColorDot(owner.color)
                Spacer(Modifier.width(8.dp))
                Text(owner.name, style = MaterialTheme.typography.titleSmall)
                Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(card.cardType.label(), style = MaterialTheme.typography.titleSmall)
        }
        Field(Res.string.card_code_value) {
            val code = card.code
            if (code == null) {
                Text(stringResource(Res.string.card_no_code), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SelectionContainer { Text(code.value, style = MaterialTheme.typography.headlineSmall) }
                Text(code.codeFormat.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        card.notes?.let { notes ->
            Field(Res.string.card_notes) { SelectionContainer { Text(notes) } }
        }
        PhotoField(card, PhotoSide.FRONT, Res.string.card_front, card.hasFront, state.scan)
        PhotoField(card, PhotoSide.BACK, Res.string.card_back, card.hasBack, state.scan)
    }
}

@Composable
private fun Field(label: StringResource, content: @Composable () -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun PhotoField(card: Card, side: PhotoSide, label: StringResource, present: Boolean, scan: Int) {
    if (!present) return
    Field(label) {
        CardPhoto(
            card, side, scan,
            Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp)),
            ContentScale.Fit,
        )
    }
}

@Composable
internal fun ErrorDialog(error: Throwable, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(errorMessage(error)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_ok)) } },
    )
}
