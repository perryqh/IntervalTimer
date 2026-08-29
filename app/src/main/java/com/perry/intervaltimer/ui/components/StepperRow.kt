package com.perry.intervaltimer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A labeled -/value/+ row, used for rounds and step/warmup/cooldown durations. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    minValue: Int = 0,
    maxValue: Int = 3600,
    valueText: String = value.toString(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange((value - step).coerceIn(minValue, maxValue)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                valueText,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(112.dp)
            )
            IconButton(onClick = { onValueChange((value + step).coerceIn(minValue, maxValue)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}
