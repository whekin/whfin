package dev.whekin.whfin.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WhfinCodeDots(
    length: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Row(
        modifier.then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
            else Modifier,
        ),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(length) { index ->
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = if (index < filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (index < filled) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                content = {},
            )
        }
    }
}

@Composable
fun WhfinNumericKeypad(
    deleteContentDescription: String,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingContentDescription: String? = null,
    onLeadingAction: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit ->
                    CodeKey(digit.toString(), enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null && leadingContentDescription != null && onLeadingAction != null) {
                CodeIconKey(leadingIcon, leadingContentDescription, enabled, onLeadingAction)
            } else {
                Spacer(Modifier.size(68.dp))
            }
            CodeKey("0", enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onDigit('0')
            }
            CodeIconKey(
                icon = Icons.AutoMirrored.Filled.Backspace,
                description = deleteContentDescription,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onBackspace()
                },
            )
        }
    }
}

@Composable
private fun CodeKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(68.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CodeIconKey(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(68.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
