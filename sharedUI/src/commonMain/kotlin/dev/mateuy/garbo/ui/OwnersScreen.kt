package dev.mateuy.garbo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mateuy.garbo.vault.Owner
import dev.mateuy.garbo.vault.OwnerColors
import dev.mateuy.garbo.vault.Vault
import dev.mateuy.garbo.vault.VaultError
import dev.mateuy.garbo.vault.VaultRepository
import dev.mateuy.garbo.vault.VaultSnapshot
import dev.mateuy.garbo.vault.VaultState
import garbo.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** The owner being created ([owner] null) or edited in the dialog. */
internal data class OwnerEdit(val owner: Owner?, val name: String, val color: String)

internal class OwnersViewModel(private val repository: VaultRepository) : ViewModel() {
    var editing by mutableStateOf<OwnerEdit?>(null)
    var deleting by mutableStateOf<Owner?>(null)
    var error by mutableStateOf<Throwable?>(null)

    /** Starts a new owner with the first palette color no owner uses yet. */
    fun startCreate(owners: List<Owner>) {
        val used = owners.map { it.color.uppercase() }.toSet()
        editing = OwnerEdit(null, "", OwnerColors.palette.firstOrNull { it !in used } ?: OwnerColors.palette.first())
    }

    fun startEdit(owner: Owner) {
        editing = OwnerEdit(owner, owner.name, owner.color)
    }

    fun saveEdit() {
        val edit = editing ?: return
        run({ editing = null }) {
            if (edit.owner == null) createOwner(edit.name, edit.color) else updateOwner(edit.owner, edit.name, edit.color)
        }
    }

    fun makeDefault(owner: Owner) = run { setDefaultOwner(owner) }

    fun confirmDelete() {
        val owner = deleting ?: return
        deleting = null
        run { deleteOwner(owner) }
    }

    private fun run(onDone: () -> Unit = {}, change: Vault.(VaultSnapshot) -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                repository.change(change)
                onDone()
            } catch (e: VaultError) {
                error = e
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnersScreen(state: VaultState.Open, navigator: Navigator) {
    val repository = LocalAppGraph.current.repository
    val viewModel = viewModel { OwnersViewModel(repository) }
    val snapshot = state.snapshot
    val defaultOwnerId = snapshot.info.defaultOwnerId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.owners_title)) },
                navigationIcon = { BackButton(navigator::back) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startCreate(snapshot.owners) }) {
                Icon(vectorResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.owners_add))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(snapshot.owners.sortedBy { it.name.lowercase() }, key = { it.ownerId }) { owner ->
                OwnerRow(
                    owner = owner,
                    cardCount = snapshot.cardsOf(owner.ownerId).size,
                    isDefault = owner.ownerId == defaultOwnerId,
                    onEdit = { viewModel.startEdit(owner) },
                    onMakeDefault = { viewModel.makeDefault(owner) },
                    onDelete = { viewModel.deleting = owner },
                )
            }
        }
    }

    viewModel.editing?.let { edit -> OwnerDialog(edit, viewModel) }
    viewModel.deleting?.let { owner ->
        AlertDialog(
            onDismissRequest = { viewModel.deleting = null },
            title = { Text(stringResource(Res.string.owner_delete_title, owner.name)) },
            text = { Text(stringResource(Res.string.owner_delete_text)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { viewModel.deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    viewModel.error?.takeUnless { viewModel.editing != null && it is VaultError.BlankName }?.let {
        ErrorDialog(it) { viewModel.error = null }
    }
}

@Composable
private fun OwnerRow(
    owner: Owner,
    cardCount: Int,
    isDefault: Boolean,
    onEdit: () -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit).testTag("owner-${owner.name}"),
        leadingContent = { ColorDot(owner.color, size = 20.dp) },
        headlineContent = { Text(owner.name) },
        supportingContent = {
            val count = pluralStringResource(Res.plurals.owners_cards, cardCount, cardCount)
            Text(if (isDefault) "$count · ${stringResource(Res.string.owners_default)}" else count)
        },
        trailingContent = {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("owner-menu-${owner.name}")) {
                Icon(vectorResource(Res.drawable.ic_more_vert), contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.action_edit)) }, onClick = { menu = false; onEdit() })
                if (!isDefault) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.owners_make_default)) },
                        leadingIcon = { Icon(vectorResource(Res.drawable.ic_star), contentDescription = null) },
                        onClick = { menu = false; onMakeDefault() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_delete)) },
                    leadingIcon = { Icon(vectorResource(Res.drawable.ic_delete), contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnerDialog(edit: OwnerEdit, viewModel: OwnersViewModel) {
    val nameError = viewModel.error is VaultError.BlankName
    AlertDialog(
        onDismissRequest = { viewModel.editing = null; viewModel.error = null },
        title = { Text(stringResource(if (edit.owner == null) Res.string.owner_new else Res.string.owner_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = edit.name,
                    onValueChange = { viewModel.editing = edit.copy(name = it) },
                    label = { Text(stringResource(Res.string.owner_name)) },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text(stringResource(Res.string.error_blank_name)) }) else null,
                    singleLine = true,
                    modifier = Modifier.testTag("owner-name"),
                )
                Text(stringResource(Res.string.owner_color), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnerColors.palette.forEach { color ->
                        val selected = color.equals(edit.color, ignoreCase = true)
                        ColorDot(
                            color,
                            size = 32.dp,
                            modifier = Modifier
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { viewModel.editing = edit.copy(color = color) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveEdit) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = {
            TextButton(onClick = { viewModel.editing = null; viewModel.error = null }) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
