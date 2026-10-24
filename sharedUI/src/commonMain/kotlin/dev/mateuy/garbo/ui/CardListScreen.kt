package dev.mateuy.garbo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mateuy.garbo.vault.Card
import dev.mateuy.garbo.vault.Owner
import dev.mateuy.garbo.vault.PhotoSide
import dev.mateuy.garbo.vault.VaultProblem
import dev.mateuy.garbo.vault.VaultRepository
import dev.mateuy.garbo.vault.VaultState
import garbo.sharedui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal class CardListViewModel(private val repository: VaultRepository) : ViewModel() {
    /** The owner whose cards are shown, or null for all. */
    var ownerFilter by mutableStateOf<String?>(null)

    fun refresh() {
        viewModelScope.launch { repository.reload() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardListScreen(state: VaultState.Open, navigator: Navigator) {
    val repository = LocalAppGraph.current.repository
    val viewModel = viewModel { CardListViewModel(repository) }
    val snapshot = state.snapshot
    // An owner removed outside Garbo (or deleted) leaves the filter on "All".
    val filter = viewModel.ownerFilter?.takeIf { snapshot.owner(it) != null }
    val cards = if (filter == null) snapshot.cards else snapshot.cardsOf(filter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    ActionIcon(Res.drawable.ic_refresh, stringResource(Res.string.action_refresh)) { viewModel.refresh() }
                    ActionIcon(Res.drawable.ic_people, stringResource(Res.string.cards_owners)) { navigator.go(Screen.Owners) }
                    ActionIcon(Res.drawable.ic_settings, stringResource(Res.string.cards_settings)) { navigator.go(Screen.Settings) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navigator.go(Screen.CardEdit(cardId = null, ownerId = filter)) }) {
                Icon(vectorResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.cards_add))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OwnerFilter(snapshot.owners.sortedBy { it.name.lowercase() }, filter) { viewModel.ownerFilter = it }
            if (snapshot.problems.isNotEmpty()) ProblemsBanner(snapshot.problems)
            if (cards.isEmpty()) {
                EmptyCards { navigator.go(Screen.CardEdit(cardId = null, ownerId = filter)) }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(cards, key = { it.cardId }) { card ->
                        CardRow(card, snapshot.owner(card.ownerId), state.scan) {
                            navigator.go(Screen.CardDetail(card.cardId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerFilter(owners: List<Owner>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(Res.string.cards_filter_all)) })
        }
        items(owners, key = { it.ownerId }) { owner ->
            FilterChip(
                selected = selected == owner.ownerId,
                onClick = { onSelect(owner.ownerId) },
                label = { Text(owner.name) },
                leadingIcon = { ColorDot(owner.color) },
                modifier = Modifier.testTag("filter-${owner.name}"),
            )
        }
    }
}

@Composable
private fun ProblemsBanner(problems: List<VaultProblem>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(vectorResource(Res.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(pluralStringResource(Res.plurals.problems_title, problems.size, problems.size), style = MaterialTheme.typography.titleSmall)
            }
            if (expanded) {
                problems.forEach { problem ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(problem.path, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (problem.kind) {
                            VaultProblem.Kind.MALFORMED -> stringResource(Res.string.problems_malformed) +
                                problem.detail?.let { ": ${it.lineSequence().first()}" }.orEmpty()
                            VaultProblem.Kind.DUPLICATE_OWNER -> stringResource(Res.string.problems_duplicate_owner)
                            VaultProblem.Kind.DUPLICATE_CARD -> stringResource(Res.string.problems_duplicate_card, problem.detail.orEmpty())
                            VaultProblem.Kind.RECOVERED -> stringResource(Res.string.problems_recovered)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCards(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.cards_empty), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text(stringResource(Res.string.cards_add)) }
        }
    }
}

@Composable
private fun CardRow(card: Card, owner: Owner?, scan: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbnail = Modifier.size(width = 72.dp, height = 46.dp).clip(RoundedCornerShape(6.dp))
            if (card.hasFront) {
                CardPhoto(card, PhotoSide.FRONT, scan, thumbnail)
            } else {
                Box(thumbnail, contentAlignment = Alignment.Center) {
                    PhotoBytes(null, Modifier.fillMaxSize())
                    Icon(vectorResource(Res.drawable.ic_image), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.cardType.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (owner != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(owner.color)
                    Spacer(Modifier.width(6.dp))
                    Text(owner.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
