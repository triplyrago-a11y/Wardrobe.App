package com.example.wardrobe.ui.screens.planner

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.model.PlannedOutfit
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.viewmodel.CalendarViewMode
import com.example.wardrobe.ui.viewmodel.PlannerViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    viewModel: PlannerViewModel,
    onNavigateToOutfits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outfits by viewModel.allOutfits.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val plannedOutfits by viewModel.allPlanned.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val currentCalendar by viewModel.currentCalendar.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val isAssignOpen by viewModel.isAssignSheetOpen.collectAsStateWithLifecycle()

    val colors = AppleTheme.colors
    var reminderEnabled by remember { mutableStateOf(false) }

    // Permission launcher for Android 13+ notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        reminderEnabled = isGranted
        viewModel.setReminderEnabled(isGranted)
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthTitle = remember(currentCalendar) { monthYearFormat.format(currentCalendar.time) }

    // Map dateString -> Outfit
    val plannedMap = remember(plannedOutfits, outfits) {
        val outfitMap = outfits.associateBy { it.id }
        plannedOutfits.associate { it.date to outfitMap[it.outfitId] }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = AppleSpacing.MaxContentWidth)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 104.dp)
        ) {
            // Header: Title & View Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Planner",
                    style = AppleTheme.typography.title.copy(color = colors.textPrimary)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Month / Week Toggle Pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.bgSecondary)
                            .clickable { viewModel.toggleViewMode() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (viewMode == CalendarViewMode.MONTH) "Month" else "Week",
                            style = AppleTheme.typography.caption.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent
                            )
                        )
                    }

                    // Notification Toggle Icon
                    IconButton(
                        onClick = {
                            if (!reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val next = !reminderEnabled
                                reminderEnabled = next
                                viewModel.setReminderEnabled(next)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = if (reminderEnabled) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            // Month Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthTitle,
                    style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary)
                )

                Row {
                    IconButton(onClick = { viewModel.prevMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Month", tint = colors.textPrimary)
                    }
                    IconButton(onClick = { viewModel.nextMonth() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month", tint = colors.textPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space8))

            // Day of Week Headers (S, M, T, W, T, F, S)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        style = AppleTheme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space8))

            // Calendar Grid (weighted so the Selected Day card and Wear Today button always stay visible above the nav pill)
            val daysInGrid = remember(currentCalendar, viewMode) {
                generateCalendarDays(currentCalendar, viewMode)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(daysInGrid) { calDay ->
                    val dateStr = calDay.dateString
                    val planned = plannedMap[dateStr]
                    val isSelected = selectedDay == dateStr

                    CalendarDayCell(
                        day = calDay,
                        plannedOutfit = planned,
                        allItems = allItems,
                        isSelected = isSelected,
                        onClick = {
                            viewModel.openAssignSheet(dateStr)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            // Selected Day Card Banner (moved upside above nav pill so Wear Today button never overlaps)
            val currentlyPlanned = plannedMap[selectedDay]
            if (currentlyPlanned != null) {
                AppleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.ScreenPadding)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppleSpacing.Space16),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Planned for $selectedDay",
                                style = AppleTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentlyPlanned.name,
                                style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary)
                            )
                        }

                        AppleButton(
                            text = "Wear Today",
                            onClick = {
                                viewModel.wearPlannedOutfit(currentlyPlanned)
                            },
                            isPrimary = true,
                            modifier = Modifier.height(36.dp),
                            testTag = "planner_wear_today_button"
                        )
                    }
                }
            }
        }
    }

    // Assign Outfit Bottom Sheet
    if (isAssignOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAssignSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.bgPrimary,
            shape = RoundedCornerShape(topStart = AppleSpacing.SheetCornerRadius, topEnd = AppleSpacing.SheetCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppleSpacing.Space24)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plan for $selectedDay",
                        style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                    )

                    if (plannedMap[selectedDay] != null) {
                        IconButton(onClick = { viewModel.removePlannedOutfit(selectedDay) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = colors.destructive)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (outfits.isEmpty()) {
                    Text(
                        text = "You don't have any saved outfits yet. Create one to assign it to this date.",
                        style = AppleTheme.typography.body.copy(color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AppleButton(
                        text = "Create Outfit",
                        onClick = {
                            viewModel.closeAssignSheet()
                            onNavigateToOutfits()
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Choose an outfit to wear on this day:",
                        style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(outfits, key = { it.id }) { outfit ->
                            val isCurrent = plannedMap[selectedDay]?.id == outfit.id

                            AppleCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.assignOutfit(outfit.id)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = outfit.name,
                                            style = AppleTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                        )
                                        Text(
                                            text = "${outfit.occasion} · ${outfit.itemIds.size} pieces",
                                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                                        )
                                    }

                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    day: CalendarDay,
    plannedOutfit: Outfit?,
    allItems: List<ClothingItem>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = AppleTheme.colors
    val outfitGarments = remember(plannedOutfit, allItems) {
        val map = allItems.associateBy { it.id }
        plannedOutfit?.itemIds?.mapNotNull { map[it] } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else if (day.isToday) 1.dp else 0.dp,
                color = if (isSelected) colors.accent else if (day.isToday) colors.accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "${day.dayNumber}",
                style = AppleTheme.typography.caption.copy(
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        !day.isCurrentMonth -> colors.textSecondary.copy(alpha = 0.35f)
                        day.isToday -> colors.accent
                        else -> colors.textPrimary
                    }
                )
            )

            // Outfit thumbnail dot or mini thumbnail
            if (plannedOutfit != null) {
                if (outfitGarments.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.accent, CircleShape)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(outfitGarments[0].imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(7.dp))
            }
        }
    }
}

data class CalendarDay(
    val dateString: String,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

fun generateCalendarDays(calendar: Calendar, viewMode: CalendarViewMode): List<CalendarDay> {
    val cal = calendar.clone() as Calendar
    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val days = mutableListOf<CalendarDay>()

    if (viewMode == CalendarViewMode.MONTH) {
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val month = cal.get(Calendar.MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun, 1=Mon...

        // Prepend previous month days
        cal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek)
        for (i in 0 until firstDayOfWeek) {
            val dateStr = format.format(cal.time)
            days.add(CalendarDay(dateStr, cal.get(Calendar.DAY_OF_MONTH), false, dateStr == todayStr))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Current month days
        while (cal.get(Calendar.MONTH) == month) {
            val dateStr = format.format(cal.time)
            days.add(CalendarDay(dateStr, cal.get(Calendar.DAY_OF_MONTH), true, dateStr == todayStr))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Postpend next month days to make complete grid of 35 or 42
        val remainder = (7 - (days.size % 7)) % 7
        for (i in 0 until remainder) {
            val dateStr = format.format(cal.time)
            days.add(CalendarDay(dateStr, cal.get(Calendar.DAY_OF_MONTH), false, dateStr == todayStr))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    } else {
        // Week view: 7 days of the current week
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        cal.add(Calendar.DAY_OF_WEEK, -dayOfWeek)
        for (i in 0..6) {
            val dateStr = format.format(cal.time)
            days.add(CalendarDay(dateStr, cal.get(Calendar.DAY_OF_MONTH), true, dateStr == todayStr))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    return days
}
