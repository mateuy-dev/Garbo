package dev.mateuy.garbo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mateuy.garbo.vault.VaultState

internal sealed interface Screen {
    data object CardList : Screen
    data class CardDetail(val cardId: String) : Screen

    /** [cardId] is null for a new card, created for [ownerId] (or the default owner). */
    data class CardEdit(val cardId: String?, val ownerId: String?) : Screen
    data object Owners : Screen
    data object Settings : Screen
}

internal class Navigator(private val backStack: MutableList<Screen>) {
    fun go(screen: Screen) {
        backStack += screen
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Replaces the current screen, e.g. a new card's editor by its detail. */
    fun replace(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }
}

/** The screens of an open vault. Leaving composition (vault closed) drops the back stack. */
@Composable
internal fun VaultNavigation(state: VaultState.Open) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.CardList) }
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { navigator.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Screen.CardList> { CardListScreen(state, navigator) }
            entry<Screen.CardDetail> { CardDetailScreen(state, it.cardId, navigator) }
            entry<Screen.CardEdit> { CardEditScreen(state, it, navigator) }
            entry<Screen.Owners> { OwnersScreen(state, navigator) }
            entry<Screen.Settings> { SettingsScreen(state, navigator) }
        },
    )
}
