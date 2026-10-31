package com.example.wardrobe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WardrobeApplication
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.model.PlannedOutfit
import com.example.wardrobe.data.worker.EveningReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarViewMode {
    MONTH, WEEK
}

data class CalendarDay(
    val dateString: String, // "YYYY-MM-DD"
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val plannedOutfit: Outfit? = null
)

class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WardrobeApplication).repository

    val allOutfits: StateFlow<List<Outfit>> = repository.allOutfits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<ClothingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlanned: StateFlow<List<PlannedOutfit>> = repository.allPlannedOutfits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val viewMode = MutableStateFlow(CalendarViewMode.MONTH)

    // Current focused month/year
    val currentCalendar = MutableStateFlow(Calendar.getInstance())

    // Selected day for assignment sheet
    val selectedDay = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )

    val isAssignSheetOpen = MutableStateFlow(false)

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == CalendarViewMode.MONTH) {
            CalendarViewMode.WEEK
        } else {
            CalendarViewMode.MONTH
        }
    }

    fun nextMonth() {
        val cal = currentCalendar.value.clone() as Calendar
        cal.add(Calendar.MONTH, 1)
        currentCalendar.value = cal
    }

    fun prevMonth() {
        val cal = currentCalendar.value.clone() as Calendar
        cal.add(Calendar.MONTH, -1)
        currentCalendar.value = cal
    }

    fun openAssignSheet(dateString: String) {
        selectedDay.value = dateString
        isAssignSheetOpen.value = true
    }

    fun closeAssignSheet() {
        isAssignSheetOpen.value = false
    }

    fun assignOutfit(outfitId: Long) {
        viewModelScope.launch {
            repository.planOutfitForDate(selectedDay.value, outfitId)
            closeAssignSheet()
        }
    }

    fun removePlannedOutfit(dateString: String) {
        viewModelScope.launch {
            repository.removePlannedOutfitForDate(dateString)
            closeAssignSheet()
        }
    }

    fun wearPlannedOutfit(outfit: Outfit) {
        viewModelScope.launch {
            repository.recordOutfitWear(outfit)
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            EveningReminderWorker.scheduleReminder(context)
        } else {
            EveningReminderWorker.cancelReminder(context)
        }
        viewModelScope.launch {
            repository.preferencesManager.setNotificationsEnabled(enabled)
        }
    }
}
