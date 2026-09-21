package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WhfinListHeader(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** One search glyph travels with the expanding input; the title keeps its measured height. */
@Composable
fun WhfinSearchHeader(
    title: String, placeholder: String, closeDescription: String, backDescription: String,
    query: String, progress: Float, focusRequester: FocusRequester,
    onQuery: (String) -> Unit, onOpen: () -> Unit, onClose: () -> Unit, onBack: () -> Unit,
    onSearch: () -> Unit,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().testTag("secondary-topbar").statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WhfinBackButton(backDescription, onBack)
        BoxWithConstraints(Modifier.weight(1f).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, Modifier.width((maxWidth - 60.dp).coerceAtLeast(0.dp)).alpha((1f - fraction * 2f).coerceIn(0f, 1f))
                .then(if (fraction > .5f) Modifier.clearAndSetSemantics { } else Modifier),
                style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val fieldWidth = 48.dp + (maxWidth - 48.dp) * fraction
            val shape = RoundedCornerShape(24.dp - 8.dp * fraction)
            Surface(Modifier.align(Alignment.CenterEnd).width(fieldWidth).clip(shape).testTag("settings-search-morph"), shape = shape,
                color = lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceContainerLow, fraction)) {
                Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).testTag("settings-search-glyph"), contentAlignment = Alignment.Center) {
                        if (fraction < .5f) WhfinIconButton(Icons.Default.Search, placeholder, onOpen, outlined = false)
                        else Icon(Icons.Default.Search, null, Modifier.size(WhfinThemeTokens.sizes.icon), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    if (fraction > .7f) {
                        BasicTextField(query, onQuery, Modifier.weight(1f).focusRequester(focusRequester)
                            .testTag("settings-search").alpha(((fraction - .7f) / .3f).coerceIn(0f, 1f)),
                            singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                            decorationBox = { input ->
                                Box {
                                    if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    input()
                                }
                            })
                        WhfinIconButton(Icons.Default.Close, closeDescription, onClose, outlined = false)
                    }
                }
            }
        }
    }
}
