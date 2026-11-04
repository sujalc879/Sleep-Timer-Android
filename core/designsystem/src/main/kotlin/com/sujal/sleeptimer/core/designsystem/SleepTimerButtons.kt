@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sujal.sleeptimer.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerConnectedChoices(
    choices: List<TimerChoice>,
    selected: Set<Int>,
    onSelect: (Int) -> Unit,
    moreDescription: String,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        overflowIndicator = { menu ->
            IconButton(onClick = menu::show, modifier = Modifier.size(48.dp)) {
                SleepTimerIcon(SleepTimerSymbol.MORE, moreDescription)
            }
        },
    ) {
        choices.forEachIndexed { index, choice ->
            customItem(
                buttonGroupContent = {
                    val interactions = remember { MutableInteractionSource() }
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    FilledTonalToggleButton(
                        checked = choice.value in selected,
                        onCheckedChange = { onSelect(choice.value) },
                        modifier = Modifier.weight(1f).animateWidth(interactions).heightIn(min = 56.dp)
                            .semantics { contentDescription = choice.description },
                        shapes = shapes,
                        interactionSource = interactions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        colors = FilledTonalToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(choice.label) }
                },
                menuContent = { menu ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = {
                        onSelect(choice.value)
                        menu.dismiss()
                    })
                },
            )
        }
    }
}

@Composable
fun SleepTimerPrimaryAction(
    running: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(96.dp),
        enabled = enabled,
        shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(40.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        SleepTimerIcon(if (running) SleepTimerSymbol.STOP else SleepTimerSymbol.PLAY, label, Modifier.size(40.dp))
    }
}
