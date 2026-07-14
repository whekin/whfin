package dev.whekin.whfin.ui.components

import androidx.compose.foundation.clickable
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
    maxHeight: Dp = 420.dp,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        modifier = modifier.heightIn(max = maxHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = selectedId == category.id
            Column(
                Modifier.clip(MaterialTheme.shapes.medium).clickable { onSelect(category) }.padding(vertical = 4.dp),
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
                )
            }
        }
    }
}

@Composable
fun CategoryAppearancePicker(
    icon: String,
    color: Int,
    onIcon: (String) -> Unit,
    onColor: (Int) -> Unit,
) {
    val icons = listOf("ShoppingCart", "Restaurant", "Home", "DirectionsBus", "MedicalServices", "VolunteerActivism", "Work", "Sell")
    val colors = listOf(0xFF78906F, 0xFFD16D5A, 0xFFE0A246, 0xFF5D7F91, 0xFF8873A8, 0xFF4C956C).map(Long::toInt)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            icons.forEach { value ->
                Surface(
                    onClick = { onIcon(value) },
                    shape = CircleShape,
                    color = if (value == icon) Color(color).copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (value == icon) androidx.compose.foundation.BorderStroke(1.5.dp, Color(color)) else null,
                ) { Icon(CategoryIcons.resolve(value), null, tint = Color(color), modifier = Modifier.padding(12.dp).size(21.dp)) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { value ->
                Surface(
                    onClick = { onColor(value) },
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = Color(value),
                            border = if (value == color) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier.size(30.dp),
                        ) {}
                    }
                }
            }
        }
    }
}
