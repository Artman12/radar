package com.onemanarmy.radar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemanarmy.radar.domain.logic.DurationFormatter
import com.onemanarmy.radar.domain.model.SavedPoint
import com.onemanarmy.radar.resources.Res
import com.onemanarmy.radar.resources.btn_cancel
import com.onemanarmy.radar.resources.btn_save
import com.onemanarmy.radar.resources.day_fri
import com.onemanarmy.radar.resources.day_mon
import com.onemanarmy.radar.resources.day_sat
import com.onemanarmy.radar.resources.day_sun
import com.onemanarmy.radar.resources.day_thu
import com.onemanarmy.radar.resources.day_tue
import com.onemanarmy.radar.resources.day_wed
import com.onemanarmy.radar.resources.points_schedule_24_7
import com.onemanarmy.radar.resources.points_schedule_24_7_desc
import com.onemanarmy.radar.resources.points_schedule_days
import com.onemanarmy.radar.resources.points_schedule_interval
import com.onemanarmy.radar.resources.points_schedule_mode_custom
import com.onemanarmy.radar.resources.points_schedule_preset_home
import com.onemanarmy.radar.resources.points_schedule_preset_work
import com.onemanarmy.radar.resources.points_schedule_presets
import com.onemanarmy.radar.resources.points_schedule_sheet_desc
import com.onemanarmy.radar.resources.points_schedule_sheet_title
import com.onemanarmy.radar.resources.settings_night_end
import com.onemanarmy.radar.resources.settings_night_start
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointScheduleBottomSheet(
    point: SavedPoint,
    onDismiss: () -> Unit,
    onSave: (
        scheduleEnabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDaysIso: Set<Int>
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scheduleEnabled by remember(point.id) { mutableStateOf(point.scheduleEnabled) }
    var startHour by remember(point.id) { mutableIntStateOf(point.scheduleStartHour) }
    var startMinute by remember(point.id) { mutableIntStateOf(point.scheduleStartMinute) }
    var endHour by remember(point.id) { mutableIntStateOf(point.scheduleEndHour) }
    var endMinute by remember(point.id) { mutableIntStateOf(point.scheduleEndMinute) }
    var activeDays by remember(point.id) {
        mutableStateOf(point.scheduleActiveDaysIso.ifEmpty { setOf(1, 2, 3, 4, 5) })
    }
    var editingStartTime by remember { mutableStateOf(false) }
    var editingEndTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(Res.string.points_schedule_sheet_title, point.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.points_schedule_sheet_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            ScheduleModeSelector(
                isScheduled = scheduleEnabled,
                onSelectMode = { scheduleEnabled = it }
            )
            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = scheduleEnabled,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 160)))
                        .using(
                            SizeTransform(
                                clip = true,
                                sizeAnimationSpec = { _, _ ->
                                    tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                }
                            )
                        )
                },
                label = "ScheduleModeContent"
            ) { isScheduled ->
                if (!isScheduled) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(Res.string.points_schedule_24_7_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(Res.string.points_schedule_presets),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                startHour = 9
                                startMinute = 0
                                endHour = 18
                                endMinute = 0
                                activeDays = setOf(1, 2, 3, 4, 5)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.points_schedule_preset_work))
                        }
                        OutlinedButton(
                            onClick = {
                                startHour = 18
                                startMinute = 0
                                endHour = 9
                                endMinute = 0
                                activeDays = setOf(1, 2, 3, 4, 5, 6, 7)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.points_schedule_preset_home))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = stringResource(Res.string.points_schedule_interval),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingStartTime = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${stringResource(Res.string.settings_night_start)}: " +
                                    DurationFormatter.formatTimeOfDay(startHour, startMinute)
                            )
                        }
                        OutlinedButton(
                            onClick = { editingEndTime = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${stringResource(Res.string.settings_night_end)}: " +
                                    DurationFormatter.formatTimeOfDay(endHour, endMinute)
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.points_schedule_days),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val dayLabels = listOf(
                        1 to stringResource(Res.string.day_mon),
                        2 to stringResource(Res.string.day_tue),
                        3 to stringResource(Res.string.day_wed),
                        4 to stringResource(Res.string.day_thu),
                        5 to stringResource(Res.string.day_fri),
                        6 to stringResource(Res.string.day_sat),
                        7 to stringResource(Res.string.day_sun)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayLabels.forEach { (isoDay, label) ->
                            val isSelected = isoDay in activeDays
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updated = if (isSelected) activeDays - isoDay else activeDays + isoDay
                                    if (updated.isNotEmpty()) {
                                        activeDays = updated
                                    }
                                },
                                label = { Text(label) },
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                                )
                            )
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.btn_cancel))
                }
                Button(
                    onClick = {
                        onSave(scheduleEnabled, startHour, startMinute, endHour, endMinute, activeDays)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(Res.string.btn_save))
                }
            }
        }
    }

    if (editingStartTime) {
        TimePickerDialogWrapper(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { editingStartTime = false },
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                editingStartTime = false
            }
        )
    }

    if (editingEndTime) {
        TimePickerDialogWrapper(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { editingEndTime = false },
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                editingEndTime = false
            }
        )
    }
}

@Composable
private fun ScheduleModeSelector(
    isScheduled: Boolean,
    onSelectMode: (Boolean) -> Unit
) {
    val options = listOf(
        false to stringResource(Res.string.points_schedule_24_7),
        true to stringResource(Res.string.points_schedule_mode_custom)
    )
    val selectedIndex = if (isScheduled) 1 else 0

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(11.dp))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (scheduledOption, label) ->
                val isSelected = scheduledOption == isScheduled
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectMode(scheduledOption) }
                        )
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
