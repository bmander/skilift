package com.skilift.app.ui.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.skilift.app.domain.model.TimeSelection
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerOverlay(
    currentSelection: TimeSelection,
    onConfirm: (TimeSelection) -> Unit,
    onDismiss: () -> Unit
) {
    // 0 = Depart, 1 = Arrive
    var modeIndex by remember {
        mutableIntStateOf(if (currentSelection is TimeSelection.ArriveBy) 1 else 0)
    }
    var isNow by remember {
        mutableStateOf(currentSelection is TimeSelection.DepartNow)
    }

    val existingMillis = when (currentSelection) {
        is TimeSelection.DepartNow -> System.currentTimeMillis()
        is TimeSelection.DepartAt -> currentSelection.epochMillis
        is TimeSelection.ArriveBy -> currentSelection.epochMillis
    }
    val existingZdt = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(existingMillis), ZoneId.systemDefault()
    )

    var selectedDate by remember { mutableStateOf(existingZdt.toLocalDate()) }
    val timePickerState = rememberTimePickerState(
        initialHour = existingZdt.hour,
        initialMinute = existingZdt.minute
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Depart / Arrive segmented button
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Depart", "Arrive").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = modeIndex == index,
                        onClick = {
                            modeIndex = index
                            if (index == 0 && isNow) {
                                // keep isNow
                            } else {
                                isNow = false
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index, count = 2
                        )
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "Now" switch (only available for Depart mode)
            if (modeIndex == 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isNow,
                        onCheckedChange = { isNow = it }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            DateSpinner(
                date = selectedDate,
                onDateChange = { selectedDate = it },
                enabled = !isNow,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box {
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.alpha(if (isNow) 0.38f else 1f)
                )
                if (isNow) {
                    // Invisible overlay to block touch interaction
                    Box(modifier = Modifier.matchParentSize())
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm / Cancel buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    if (isNow && modeIndex == 0) {
                        onConfirm(TimeSelection.DepartNow)
                    } else {
                        val selectedTime = LocalTime.of(
                            timePickerState.hour, timePickerState.minute
                        )
                        val zdt = ZonedDateTime.of(
                            selectedDate, selectedTime, ZoneId.systemDefault()
                        )
                        val epochMillis = zdt.toInstant().toEpochMilli()
                        if (modeIndex == 0) {
                            onConfirm(TimeSelection.DepartAt(epochMillis))
                        } else {
                            onConfirm(TimeSelection.ArriveBy(epochMillis))
                        }
                    }
                }) {
                    Text("Confirm")
                }
            }
        }
    }
}
