package dev.whekin.whfin.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class WhfinAmountKey(val label: String) {
    DIGIT_0("0"),
    DIGIT_00("00"),
    DIGIT_1("1"),
    DIGIT_2("2"),
    DIGIT_3("3"),
    DIGIT_4("4"),
    DIGIT_5("5"),
    DIGIT_6("6"),
    DIGIT_7("7"),
    DIGIT_8("8"),
    DIGIT_9("9"),
    DECIMAL("."),
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
    PERCENT("%"),
    EQUALS("="),
    BACKSPACE(""),
}

/** Compact calculator used by amount-only entry surfaces; calculation policy stays in the feature. */
@Composable
fun WhfinAmountKeypad(
    deleteContentDescription: String,
    onKey: (WhfinAmountKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val send: (WhfinAmountKey) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        onKey(key)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmountKeyRow(
            keys = listOf(
                WhfinAmountKey.DIGIT_7,
                WhfinAmountKey.DIGIT_8,
                WhfinAmountKey.DIGIT_9,
                WhfinAmountKey.DIVIDE,
                WhfinAmountKey.BACKSPACE,
            ),
            deleteContentDescription = deleteContentDescription,
            enabled = enabled,
            onKey = send,
        )
        AmountKeyRow(
            keys = listOf(
                WhfinAmountKey.DIGIT_4,
                WhfinAmountKey.DIGIT_5,
                WhfinAmountKey.DIGIT_6,
                WhfinAmountKey.MULTIPLY,
                WhfinAmountKey.PERCENT,
            ),
            deleteContentDescription = deleteContentDescription,
            enabled = enabled,
            onKey = send,
        )
        Row(
            Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.weight(4f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmountKeyRow(
                    keys = listOf(
                        WhfinAmountKey.DIGIT_1,
                        WhfinAmountKey.DIGIT_2,
                        WhfinAmountKey.DIGIT_3,
                        WhfinAmountKey.SUBTRACT,
                    ),
                    deleteContentDescription = deleteContentDescription,
                    enabled = enabled,
                    onKey = send,
                )
                AmountKeyRow(
                    keys = listOf(
                        WhfinAmountKey.DIGIT_00,
                        WhfinAmountKey.DIGIT_0,
                        WhfinAmountKey.DECIMAL,
                        WhfinAmountKey.ADD,
                    ),
                    deleteContentDescription = deleteContentDescription,
                    enabled = enabled,
                    onKey = send,
                )
            }
            AmountKey(
                key = WhfinAmountKey.EQUALS,
                deleteContentDescription = deleteContentDescription,
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { send(WhfinAmountKey.EQUALS) },
            )
        }
    }
}

@Composable
private fun AmountKeyRow(
    keys: List<WhfinAmountKey>,
    deleteContentDescription: String,
    enabled: Boolean,
    onKey: (WhfinAmountKey) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            AmountKey(
                key = key,
                deleteContentDescription = deleteContentDescription,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(52.dp),
                onClick = { onKey(key) },
            )
        }
    }
}

@Composable
private fun AmountKey(
    key: WhfinAmountKey,
    deleteContentDescription: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isOperator = key in setOf(
        WhfinAmountKey.ADD,
        WhfinAmountKey.SUBTRACT,
        WhfinAmountKey.MULTIPLY,
        WhfinAmountKey.DIVIDE,
        WhfinAmountKey.PERCENT,
    )
    val color = when {
        key == WhfinAmountKey.EQUALS -> MaterialTheme.colorScheme.primaryContainer
        isOperator -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag("whfin-amount-key-${key.name}").then(
            if (key == WhfinAmountKey.BACKSPACE) {
                Modifier.semantics { contentDescription = deleteContentDescription }
            } else Modifier,
        ),
        shape = MaterialTheme.shapes.medium,
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (key == WhfinAmountKey.BACKSPACE) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    key.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (key == WhfinAmountKey.EQUALS) FontWeight.Medium else FontWeight.Normal,
                    color = if (key == WhfinAmountKey.EQUALS) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(name = "amount_keypad_light", widthDp = 360)
@Preview(name = "amount_keypad_dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WhfinAmountKeypadPreview() {
    WhfinTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            WhfinAmountKeypad(
                deleteContentDescription = "Delete digit",
                onKey = {},
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            )
        }
    }
}
