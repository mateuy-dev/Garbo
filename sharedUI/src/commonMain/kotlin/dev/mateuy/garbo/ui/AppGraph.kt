package dev.mateuy.garbo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import dev.mateuy.garbo.vault.PickedFolder
import dev.mateuy.garbo.vault.SettingsVaultStore
import dev.mateuy.garbo.vault.StoredVault
import dev.mateuy.garbo.vault.VaultRepository
import dev.mateuy.garbo.vault.VaultSession
import dev.mateuy.garbo.vault.VaultStore
import dev.mateuy.garbo.vault.reopenStoredFolder

/** The app's long-lived objects, created once per process. */
class AppGraph(
    store: VaultStore = SettingsVaultStore(Settings()),
    reopen: (StoredVault) -> PickedFolder = ::reopenStoredFolder,
) {
    val repository = VaultRepository()
    val session = VaultSession(repository, store, reopen)
}

internal val appGraph: AppGraph by lazy { AppGraph() }

internal val LocalAppGraph = staticCompositionLocalOf { appGraph }
