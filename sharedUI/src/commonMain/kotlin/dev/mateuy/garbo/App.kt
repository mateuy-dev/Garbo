package dev.mateuy.garbo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.mateuy.garbo.theme.AppTheme
import dev.mateuy.garbo.ui.LocalAppGraph
import dev.mateuy.garbo.ui.SetupScreen
import dev.mateuy.garbo.ui.VaultNavigation
import dev.mateuy.garbo.vault.VaultState
import kotlinx.coroutines.launch

@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}
) = AppTheme(onThemeChanged) {
    val graph = LocalAppGraph.current
    val state by graph.repository.state.collectAsState()
    val scope = rememberCoroutineScope()

    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        graph.session.restore()
        restored = true
    }

    // Other apps (a sync tool) may have changed the vault while Garbo was in the background.
    LifecycleResumeEffect(Unit) {
        scope.launch { graph.repository.reload() }
        onPauseOrDispose {}
    }

    // The setup screen stays while a vault is loading, so that what it started is not cancelled.
    if (!restored) return@AppTheme
    when (val current = state) {
        is VaultState.Open -> VaultNavigation(current)
        else -> SetupScreen(error = (current as? VaultState.Closed)?.error, busy = current == VaultState.Loading)
    }
}
