package com.example.wardrobe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WardrobeApplication
import com.example.wardrobe.data.model.ClothingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryBreakdown(
    val category: String,
    val count: Int,
    val percentage: Float
)

data class WardrobeInsights(
    val totalValue: Double,
    val averageCostPerWear: Double,
    val totalWears: Int,
    val mostWornItem: ClothingItem?,
    val categoryBreakdown: List<CategoryBreakdown>
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WardrobeApplication).repository
    val preferences = repository.preferencesManager

    val allItems: StateFlow<List<ClothingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userName: StateFlow<String> = preferences.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Alex")

    val stylePreference: StateFlow<String> = preferences.stylePreference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Modern Minimalist")

    val themeMode: StateFlow<String> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val notificationsEnabled: StateFlow<Boolean> = preferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val exportJsonString = MutableStateFlow<String?>(null)
    val importStatusMessage = MutableStateFlow<String?>(null)

    fun updateName(name: String, onCloudSynced: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                preferences.setUserName(trimmed)
                val result = com.example.wardrobe.data.remote.SupabaseClientManager.updateUserProfile(
                    fullName = trimmed
                )
                result.onSuccess {
                    onCloudSynced?.invoke(true, "Profile updated in Supabase!")
                }.onFailure { err ->
                    onCloudSynced?.invoke(false, err.localizedMessage ?: "Profile saved locally.")
                }
            }
        }
    }

    fun updateStylePreference(style: String, onCloudSynced: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            preferences.setStylePreference(style)
            val result = com.example.wardrobe.data.remote.SupabaseClientManager.updateUserProfile(
                stylePreference = style
            )
            result.onSuccess {
                onCloudSynced?.invoke(true, "Style preference synced to Supabase!")
            }.onFailure { err ->
                onCloudSynced?.invoke(false, err.localizedMessage ?: "Style preference saved locally.")
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            val json = repository.exportWardrobeJson()
            exportJsonString.value = json
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            val success = repository.importWardrobeJson(json)
            importStatusMessage.value = if (success) "Wardrobe successfully imported!" else "Import failed. Invalid JSON."
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    fun reloadDemoData() {
        viewModelScope.launch {
            repository.reloadDemoData()
        }
    }

    fun resetAuthIntro() {
        viewModelScope.launch {
            com.example.wardrobe.data.remote.SupabaseClientManager.signOut()
            preferences.setAuthIntroCompleted(false)
        }
    }

    fun calculateInsights(items: List<ClothingItem>): WardrobeInsights {
        val totalValue = items.sumOf { it.price }
        val totalWears = items.sumOf { it.wearCount }
        val avgCostPerWear = if (totalWears > 0) totalValue / totalWears else totalValue
        val mostWorn = items.maxByOrNull { it.wearCount }

        val totalCount = items.size.toFloat().coerceAtLeast(1f)
        val categories = listOf("Tops", "Bottoms", "Dresses", "Outerwear", "Shoes", "Accessories")
        val breakdown = categories.map { cat ->
            val count = items.count { it.category.equals(cat, ignoreCase = true) }
            CategoryBreakdown(cat, count, count / totalCount)
        }

        return WardrobeInsights(
            totalValue = totalValue,
            averageCostPerWear = avgCostPerWear,
            totalWears = totalWears,
            mostWornItem = mostWorn,
            categoryBreakdown = breakdown
        )
    }
}
