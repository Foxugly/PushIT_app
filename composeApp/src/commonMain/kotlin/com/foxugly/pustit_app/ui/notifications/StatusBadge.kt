package com.foxugly.pustit_app.ui.notifications

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (status.lowercase()) {
        "delivered" -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        "sent" -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        "failed" -> colorScheme.errorContainer to colorScheme.onErrorContainer
        else -> colorScheme.surface to colorScheme.outline // "pending" → outline style
    }

    val isPending = status.lowercase() == "pending"

    Surface(
        modifier = modifier.then(
            if (isPending) Modifier.border(1.dp, colorScheme.outline, MaterialTheme.shapes.small)
            else Modifier
        ),
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
