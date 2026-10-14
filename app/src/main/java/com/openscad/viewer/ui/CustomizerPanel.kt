package com.openscad.viewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.openscad.core.CustomizerModel
import com.openscad.core.CustomizerParameter
import com.openscad.core.ParamKind

/**
 * Renders the Customizer surface produced by `CustomizerParser`.
 *
 * Section headers come from `[Group]` block comments, control types from the
 * trailing annotation (`[min:step:max]`, `[a, b, c]`, …).
 */
@Composable
fun CustomizerPanel(
    model: CustomizerModel,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model.isEmpty) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No Customizer parameters found.\n\nAdd metadata comments to your model, e.g.\n/* [Size] */\n// Width\nwidth = 20;  // [10:50]",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        for (section in model.visibleSections) {
            Text(
                text = (section.name ?: "Parameters").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
            )
            for (parameter in section.parameters) {
                ParameterCard(
                    parameter = parameter,
                    value = values[parameter.name] ?: parameter.defaultValue,
                    onValueChange = { onValueChange(parameter.name, it) },
                )
            }
        }

        if (model.hiddenParameters.isNotEmpty()) {
            Text(
                text = "${model.hiddenParameters.size} hidden parameter(s): " +
                    model.hiddenParameters.joinToString { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp, 8.dp),
            )
        }
    }
}

@Composable
private fun ParameterCard(
    parameter: CustomizerParameter,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(parameter.displayName, style = MaterialTheme.typography.titleSmall)
                    parameter.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = displayValue(parameter, value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(6.dp))

            when (parameter.kind) {
                ParamKind.SLIDER -> {
                    val range = parameter.range!!
                    Slider(
                        value = value.toFloatOrNull()?.coerceIn(range.min.toFloat(), range.max.toFloat())
                            ?: range.min.toFloat(),
                        onValueChange = { onValueChange(compact(it.toDouble())) },
                        valueRange = range.min.toFloat()..range.max.toFloat(),
                        steps = (range.steps - 1).coerceAtLeast(0),
                    )
                }

                ParamKind.DROPDOWN -> ParameterDropdown(parameter, value, onValueChange)

                ParamKind.CHECKBOX -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (value == "true") "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = value == "true",
                        onCheckedChange = { onValueChange(it.toString()) },
                    )
                }

                ParamKind.NUMBER, ParamKind.TEXT -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (parameter.kind == ParamKind.NUMBER) KeyboardType.Number else KeyboardType.Text,
                    ),
                )

                ParamKind.VECTOR, ParamKind.UNKNOWN -> Text(
                    text = value,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParameterDropdown(
    parameter: CustomizerParameter,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = parameter.choices.firstOrNull { it.value == value } ?: parameter.choices.firstOrNull()

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = current?.label ?: value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            parameter.choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        onValueChange(choice.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun displayValue(parameter: CustomizerParameter, value: String): String = when (parameter.kind) {
    ParamKind.SLIDER, ParamKind.NUMBER -> value.toDoubleOrNull()?.let(::compact) ?: value
    ParamKind.CHECKBOX -> if (value == "true") "on" else "off"
    ParamKind.DROPDOWN ->
        parameter.choices.firstOrNull { it.value == value }?.label ?: value
    else -> value
}

private fun compact(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
