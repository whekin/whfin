package dev.whekin.whfin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import dev.whekin.whfin.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.ui.CategoryIcons

/** Сетка категорий — единый вид для пикера в ленте и формы добавления. */
@Composable
fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (CategoryEntity) -> Unit,
    /** [Dp.Unspecified] — сетка занимает высоту, отданную ей родителем (например `weight`). */
    maxHeight: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),
        modifier = modifier.then(if (maxHeight == Dp.Unspecified) Modifier else Modifier.heightIn(max = maxHeight)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = selectedId == category.id
            Column(
                Modifier.clip(MaterialTheme.shapes.medium).selectable(selected, enabled = enabled, role = Role.RadioButton,
                    onClick = { onSelect(category) }).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) Color(category.color).copy(alpha = .22f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(category.color)) else null,
                ) {
                    Icon(
                        CategoryIcons.resolve(category.icon), null,
                        tint = Color(category.color), modifier = Modifier.padding(13.dp).size(22.dp),
                    )
                }
                Text(
                    category.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CategoryAppearancePicker(icon: String, color: Int, onIcon: (String) -> Unit, onColor: (Int) -> Unit, enabled: Boolean = true) {
    val originalIcon = androidx.compose.runtime.saveable.rememberSaveable { icon }
    val baseIcons = listOf("ShoppingCart", "Restaurant", "Home", "DirectionsBus", "MedicalServices", "VolunteerActivism", "Work", "Sell")
    val icons = (baseIcons + originalIcon).distinct()
    val labels = listOf(R.string.category_icon_shopping, R.string.category_icon_food, R.string.category_icon_home,
        R.string.category_icon_transport, R.string.category_icon_health, R.string.category_icon_giving,
        R.string.category_icon_work, R.string.category_icon_other)
    val initialIndex = androidx.compose.runtime.remember { icons.indexOf(icon) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        dev.whekin.whfin.core.ui.WhfinChoiceRail(revealIndex = initialIndex) {
            icons.forEachIndexed { index, value -> item(key = value) {
                val description = stringResource(labels.getOrElse(index) { R.string.category_icon_current })
                Surface(onClick = { onIcon(value) }, enabled = enabled, shape = CircleShape,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = description; selected = value == icon },
                    color = if (value == icon) Color(color).copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (value == icon) androidx.compose.foundation.BorderStroke(1.5.dp, Color(color)) else null) {
                    Icon(CategoryIcons.resolve(value), null, tint = Color(color), modifier = Modifier.padding(12.dp).size(21.dp))
                }
            } }
        }
        CategoryColorPicker(color, onColor, enabled)
    }
}

@Composable
fun CategoryColorPicker(color: Int, onColor: (Int) -> Unit, enabled: Boolean = true) {
    val originalColor = androidx.compose.runtime.saveable.rememberSaveable { color }
    val colors = (listOf(0xFF78906F, 0xFFD16D5A, 0xFFE0A246, 0xFF5D7F91, 0xFF8873A8, 0xFF4C956C).map(Long::toInt) + originalColor).distinct()
    val labels = listOf(R.string.category_color_sage, R.string.category_color_coral, R.string.category_color_amber,
        R.string.category_color_blue, R.string.category_color_purple, R.string.category_color_green)
    dev.whekin.whfin.core.ui.WhfinColorPicker(colors.mapIndexed { index, value ->
        dev.whekin.whfin.core.ui.WhfinChoice(value, stringResource(labels.getOrElse(index) { R.string.category_color_current }))
    }, color, onColor, enabled)
}
