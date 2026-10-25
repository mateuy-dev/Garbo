package dev.mateuy.garbo.ui

import androidx.compose.runtime.Composable
import dev.mateuy.garbo.vault.NotAnImageException
import dev.mateuy.garbo.vault.VaultError
import garbo.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun errorMessage(error: Throwable): String = when (error) {
    is VaultError.NotAVault -> stringResource(Res.string.error_not_a_vault)
    is VaultError.AlreadyAVault -> stringResource(Res.string.setup_open_instead_title)
    is VaultError.FolderNotEmpty -> stringResource(Res.string.error_folder_not_empty)
    is VaultError.UnsupportedFormat -> stringResource(Res.string.error_unsupported_format, error.version)
    is VaultError.Unreadable -> stringResource(Res.string.error_unreadable, error.cause?.message.orEmpty())
    is VaultError.Unreachable -> stringResource(Res.string.error_unreachable)
    is VaultError.BlankName -> stringResource(Res.string.error_blank_name)
    is VaultError.OwnerHasCards -> stringResource(Res.string.error_owner_has_cards)
    is VaultError.OwnerIsDefault -> stringResource(Res.string.error_owner_is_default)
    is VaultError.NotFound -> stringResource(Res.string.error_not_found)
    is VaultError.Io -> stringResource(Res.string.error_io, error.message.orEmpty())
    is NotAnImageException -> stringResource(Res.string.error_not_an_image)
    else -> error.message.orEmpty()
}
