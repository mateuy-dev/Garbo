package dev.mateuy.garbo.ui

import androidx.compose.runtime.Composable
import dev.mateuy.garbo.vault.CardType
import dev.mateuy.garbo.vault.CodeFormat
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

@Composable
internal fun CardType.label(): String = stringResource(
    when (this) {
        CardType.LOYALTY -> Res.string.type_loyalty
        CardType.MEMBERSHIP -> Res.string.type_membership
        CardType.LIBRARY -> Res.string.type_library
        CardType.ID -> Res.string.type_id
        CardType.OTHER -> Res.string.type_other
    },
)

@Composable
internal fun CodeFormat.label(): String = stringResource(
    when (this) {
        CodeFormat.QR -> Res.string.format_qr
        CodeFormat.CODE128 -> Res.string.format_code128
        CodeFormat.CODE39 -> Res.string.format_code39
        CodeFormat.EAN13 -> Res.string.format_ean13
        CodeFormat.EAN8 -> Res.string.format_ean8
        CodeFormat.UPCA -> Res.string.format_upca
        CodeFormat.PDF417 -> Res.string.format_pdf417
        CodeFormat.AZTEC -> Res.string.format_aztec
        CodeFormat.DATAMATRIX -> Res.string.format_datamatrix
        CodeFormat.TEXT -> Res.string.format_text
    },
)
