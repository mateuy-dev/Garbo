package dev.mateuy.garbo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mateuy.garbo.vault.Card
import dev.mateuy.garbo.vault.CardDraft
import dev.mateuy.garbo.vault.NotAnImageException
import dev.mateuy.garbo.vault.PhotoChange
import dev.mateuy.garbo.vault.PhotoSide
import dev.mateuy.garbo.vault.VaultError
import dev.mateuy.garbo.vault.VaultRepository
import dev.mateuy.garbo.vault.VaultSnapshot
import dev.mateuy.garbo.vault.VaultState
import dev.mateuy.garbo.vault.normalizePhoto
import garbo.sharedui.generated.resources.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** A photo being edited: what will happen on save, and the bytes to preview if known. */
internal data class PhotoEdit(val change: PhotoChange = PhotoChange.Keep, val preview: ByteArray? = null)

/**
 * The card editor. Nothing is written until [save]; leaving the screen discards the changes.
 * [existing] is null for a new card.
 */
internal class CardEditViewModel(
    private val repository: VaultRepository,
    snapshot: VaultSnapshot,
    cardId: String?,
    ownerId: String?,
) : ViewModel() {
    val existing: Card? = cardId?.let { id -> snapshot.cards.firstOrNull { it.cardId == id } }

    var name by mutableStateOf(existing?.name.orEmpty())
    var ownerId by mutableStateOf(existing?.ownerId ?: ownerId ?: snapshot.defaultOwner?.ownerId.orEmpty())

    var isId by mutableStateOf(existing?.isId == true)
    var codeValue by mutableStateOf(existing?.code?.value.orEmpty())
    var notes by mutableStateOf(existing?.notes.orEmpty())
    var front by mutableStateOf(PhotoEdit())
    var back by mutableStateOf(PhotoEdit())

    var error by mutableStateOf<Throwable?>(null)
    var saving by mutableStateOf(false)
        private set

    init {
        existing?.let { card ->
            viewModelScope.launch {
                if (card.hasFront) loadPreview(card, PhotoSide.FRONT)
                if (card.hasBack) loadPreview(card, PhotoSide.BACK)
            }
        }
    }

    private suspend fun loadPreview(card: Card, side: PhotoSide) {
        val bytes = repository.readPhoto(card, side)
        // The user may have replaced or removed the photo while it was loading.
        if (photo(side).change == PhotoChange.Keep) setPhoto(side, PhotoEdit(preview = bytes))
    }

    fun photo(side: PhotoSide): PhotoEdit = if (side == PhotoSide.FRONT) front else back

    private fun setPhoto(side: PhotoSide, edit: PhotoEdit) {
        if (side == PhotoSide.FRONT) front = edit else back = edit
    }

    fun importPhoto(side: PhotoSide, file: PlatformFile) {
        viewModelScope.launch {
            try {
                val jpeg = normalizePhoto(file.readBytes())
                setPhoto(side, PhotoEdit(PhotoChange.Replace(jpeg), jpeg))
            } catch (e: NotAnImageException) {
                error = e
            }
        }
    }

    fun removePhoto(side: PhotoSide) = setPhoto(side, PhotoEdit(PhotoChange.Remove, null))

    fun save(onSaved: (Card) -> Unit) {
        if (saving) return
        val draft = CardDraft(
            cardId = existing?.cardId,
            ownerId = ownerId,
            name = name,
            isId = isId,
            codeValue = codeValue,
            notes = notes,
            front = front.change,
            back = back.change,
        )
        saving = true
        error = null
        viewModelScope.launch {
            try {
                onSaved(repository.change { saveCard(draft, existing, it.owners) })
            } catch (e: VaultError) {
                error = e
            } finally {
                saving = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardEditScreen(state: VaultState.Open, key: Screen.CardEdit, navigator: Navigator) {
    val repository = LocalAppGraph.current.repository
    val viewModel = viewModel { CardEditViewModel(repository, state.snapshot, key.cardId, key.ownerId) }
    val owners = state.snapshot.owners.sortedBy { it.name.lowercase() }

    // The card was removed by another app while being edited.
    if (key.cardId != null && viewModel.existing == null) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (key.cardId == null) Res.string.card_new else Res.string.card_edit)) },
                navigationIcon = { BackButton(navigator::back) },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save { saved ->
                                if (key.cardId == null) navigator.replace(Screen.CardDetail(saved.cardId)) else navigator.back()
                            }
                        },
                        enabled = !viewModel.saving,
                    ) { Text(stringResource(Res.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val nameError = viewModel.error is VaultError.BlankName
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(Res.string.card_name)) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(Res.string.error_blank_name)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("card-name"),
            )
            Picker(
                label = stringResource(Res.string.card_owner),
                selected = owners.firstOrNull { it.ownerId == viewModel.ownerId },
                options = owners,
                optionLabel = { it?.name ?: stringResource(Res.string.owner_unknown) },
                onSelect = { owner -> owner?.let { viewModel.ownerId = it.ownerId } },
                leading = { owner -> owner?.let { ColorDot(it.color) } },
                modifier = Modifier.testTag("card-owner"),
            )
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = viewModel.isId, role = Role.Switch) { viewModel.isId = it }
                    .testTag("card-id"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.card_identity), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = viewModel.isId, onCheckedChange = null)
            }
            OutlinedTextField(
                value = viewModel.codeValue,
                onValueChange = { viewModel.codeValue = it },
                label = { Text(stringResource(Res.string.card_code_value)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("card-code"),
            )
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text(stringResource(Res.string.card_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("card-notes"),
            )
            PhotoEditor(viewModel, PhotoSide.FRONT, Res.string.card_front)
            PhotoEditor(viewModel, PhotoSide.BACK, Res.string.card_back)
        }
    }

    viewModel.error?.takeUnless { it is VaultError.BlankName }?.let { ErrorDialog(it) { viewModel.error = null } }
}

@Composable
private fun PhotoEditor(viewModel: CardEditViewModel, side: PhotoSide, label: StringResource) {
    val launcher = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        file?.let { viewModel.importPhoto(side, it) }
    }
    val photo = viewModel.photo(side)
    val hasPhoto = photo.preview != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        val frame = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(12.dp))
        if (hasPhoto) {
            PhotoBytes(photo.preview, frame)
        } else {
            Box(frame, contentAlignment = Alignment.Center) {
                PhotoBytes(null, Modifier.fillMaxSize())
                Icon(vectorResource(Res.drawable.ic_image), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { launcher.launch() }) {
                Text(stringResource(if (hasPhoto) Res.string.card_photo_replace else Res.string.card_photo_add))
            }
            if (hasPhoto) {
                TextButton(onClick = { viewModel.removePhoto(side) }) { Text(stringResource(Res.string.card_photo_remove)) }
            }
        }
    }
}
