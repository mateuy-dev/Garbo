package dev.mateuy.garbo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mateuy.garbo.vault.Card
import dev.mateuy.garbo.vault.PhotoSide
import garbo.sharedui.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** Parses an owner color (`#RRGGBB`); invalid values were already replaced when loading. */
internal fun ownerColor(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(16))

@Composable
internal fun ColorDot(color: String, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).clip(CircleShape).background(ownerColor(color)))
}

/** Marks a card that is an identity document. */
@Composable
internal fun IdBadge() {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(Res.string.card_id_badge),
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun ActionIcon(icon: DrawableResource, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(vectorResource(icon), contentDescription = description) }
}

@Composable
internal fun BackButton(onBack: () -> Unit) =
    ActionIcon(Res.drawable.ic_arrow_back, stringResource(Res.string.action_back), onBack)

/**
 * A photo of [card] read from the vault. [scan] (from the vault state) makes it read the file
 * again after each scan, so a replaced photo shows up.
 */
@Composable
internal fun CardPhoto(
    card: Card,
    side: PhotoSide,
    scan: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val repository = LocalAppGraph.current.repository
    val bytes by produceState<ByteArray?>(null, card, side, scan) {
        value = runCatching { repository.readPhoto(card, side) }.getOrNull()
    }
    PhotoBytes(bytes, modifier, contentScale)
}

@Composable
internal fun PhotoBytes(bytes: ByteArray?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (bytes == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(model = bytes, contentDescription = null, contentScale = contentScale, modifier = modifier)
    }
}

/** A read-only field that opens a menu of [options] to choose from. */
@Composable
internal fun <T> Picker(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leading?.let { { it(selected) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field would take the click to focus itself; this layer opens the menu instead.
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = leading?.let { { it(option) } },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
