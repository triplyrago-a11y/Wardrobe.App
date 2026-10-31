package com.example.wardrobe.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WardrobeApplication
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.model.PlannedOutfit
import com.example.wardrobe.data.remote.ClothingItemAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortMode(val label: String) {
    RECENT("Recent"),
    MOST_WORN("Most Worn"),
    A_TO_Z("A to Z"),
    PRICE("Price")
}

data class FilterState(
    val category: String = "All",
    val color: String = "All",
    val season: String = "All",
    val occasion: String = "All",
    val favoritesOnly: Boolean = false
)

class WardrobeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WardrobeApplication).repository

    val allItems: StateFlow<List<ClothingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOutfits: StateFlow<List<Outfit>> = repository.allOutfits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlanned: StateFlow<List<PlannedOutfit>> = repository.allPlannedOutfits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search, Category, Filter & Sort
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filters = MutableStateFlow(FilterState())
    val filters: StateFlow<FilterState> = _filters.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.RECENT)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    // Multi-select for bulk actions
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    // Selected item for detail sheet
    private val _selectedDetailItem = MutableStateFlow<ClothingItem?>(null)
    val selectedDetailItem: StateFlow<ClothingItem?> = _selectedDetailItem.asStateFlow()

    // Toast state
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Add item form state
    val isAnalyzingImage = MutableStateFlow(false)
    val analysisResult = MutableStateFlow<ClothingItemAnalysis?>(null)

    // Today's outfit hero recommendation
    private val _todayOutfitSeed = MutableStateFlow(0)
    val todayOutfit: StateFlow<Outfit?> = combine(allOutfits, allItems, _todayOutfitSeed) { outfits, items, seed ->
        if (outfits.isNotEmpty()) {
            val index = (seed % outfits.size + outfits.size) % outfits.size
            outfits[index]
        } else if (items.size >= 2) {
            // Virtual outfit suggestion from items
            Outfit(
                id = -1,
                name = "Today's Minimal Look",
                itemIds = items.take(3).map { it.id },
                occasion = "Casual",
                season = "All-Season"
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered and Sorted items list
    val filteredItems: StateFlow<List<ClothingItem>> = combine(
        allItems,
        _searchQuery,
        _filters,
        _sortMode
    ) { items, query, filter, sort ->
        var result = items

        if (filter.category != "All") {
            result = result.filter { it.category.equals(filter.category, ignoreCase = true) }
        }
        if (filter.color != "All") {
            result = result.filter { it.colors.any { c -> c.contains(filter.color, ignoreCase = true) } }
        }
        if (filter.season != "All") {
            result = result.filter { it.seasons.any { s -> s.contains(filter.season, ignoreCase = true) } }
        }
        if (filter.occasion != "All") {
            result = result.filter { it.occasions.any { o -> o.contains(filter.occasion, ignoreCase = true) } }
        }
        if (filter.favoritesOnly) {
            result = result.filter { it.favorite }
        }
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.brand.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                it.material.contains(query, ignoreCase = true)
            }
        }

        when (sort) {
            SortMode.RECENT -> result.sortedByDescending { it.createdAt }
            SortMode.MOST_WORN -> result.sortedByDescending { it.wearCount }
            SortMode.A_TO_Z -> result.sortedBy { it.name.lowercase(Locale.ROOT) }
            SortMode.PRICE -> result.sortedByDescending { it.price }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _filters.value = _filters.value.copy(category = category)
    }

    fun setFilters(filters: FilterState) {
        _filters.value = filters
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun openDetail(item: ClothingItem) {
        _selectedDetailItem.value = item
    }

    fun closeDetail() {
        _selectedDetailItem.value = null
    }

    fun toggleFavorite(item: ClothingItem) {
        viewModelScope.launch {
            repository.toggleFavoriteItem(item.id)
            showToast(if (!item.favorite) "Added to Favorites" else "Removed from Favorites")
        }
    }

    fun toggleFavoriteOutfit(outfit: Outfit) {
        viewModelScope.launch {
            repository.toggleFavoriteOutfit(outfit.id)
            showToast(if (!outfit.favorite) "Outfit Favorited" else "Removed from Favorites")
        }
    }

    fun wearTodayOutfit(outfit: Outfit) {
        viewModelScope.launch {
            repository.recordOutfitWear(outfit)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            repository.planOutfitForDate(todayStr, outfit.id)
            showToast("Worn today! Wear count updated.")
        }
    }

    fun regenerateTodayOutfit() {
        _todayOutfitSeed.value += 1
    }

    fun toggleMultiSelect(item: ClothingItem) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(item.id)) {
            current.remove(item.id)
            if (current.isEmpty()) {
                _isMultiSelectMode.value = false
            }
        } else {
            current.add(item.id)
            _isMultiSelectMode.value = true
        }
        _selectedItemIds.value = current
    }

    fun clearMultiSelect() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun bulkDeleteSelected() {
        viewModelScope.launch {
            val ids = _selectedItemIds.value.toList()
            repository.deleteItems(ids)
            showToast("Deleted ${ids.size} items")
            clearMultiSelect()
        }
    }

    fun deleteItem(item: ClothingItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            closeDetail()
            showToast("Item deleted")
        }
    }

    private val isSavingItemLock = java.util.concurrent.atomic.AtomicBoolean(false)

    fun saveItem(item: ClothingItem, isNew: Boolean, onDone: () -> Unit) {
        if (!isSavingItemLock.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                if (isNew) {
                    repository.insertItem(item)
                    showToast("Item added to wardrobe")
                } else {
                    repository.updateItem(item)
                    showToast("Item updated")
                }
                onDone()
            } catch (e: Exception) {
                showToast("Failed to save item: ${e.message ?: "Storage error"}")
            } finally {
                isSavingItemLock.set(false)
            }
        }
    }

    fun analyzeImageWithGemini(bitmap: Bitmap) {
        viewModelScope.launch {
            isAnalyzingImage.value = true
            val result = repository.geminiService.analyzeClothingImage(bitmap)
            result.onSuccess {
                analysisResult.value = it
                showToast("Gemini auto-filled details!")
            }.onFailure {
                showToast("Auto-fill ready with smart defaults")
            }
            isAnalyzingImage.value = false
        }
    }

    fun quickSaveCapturedItem(imageUri: String, bitmap: Bitmap?, onDone: () -> Unit) {
        if (!isSavingItemLock.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                isAnalyzingImage.value = true
                val analysis = if (bitmap != null) {
                    repository.geminiService.analyzeClothingImage(bitmap).getOrNull()
                } else null

                val newItem = ClothingItem(
                    id = 0,
                    name = analysis?.suggestedName?.takeIf { it.isNotBlank() } ?: "Captured Garment",
                    imageUri = imageUri,
                    category = analysis?.category?.takeIf { it.isNotBlank() } ?: "Tops",
                    colors = analysis?.colors?.takeIf { it.isNotEmpty() } ?: listOf("Neutral"),
                    pattern = analysis?.pattern?.takeIf { it.isNotBlank() } ?: "Solid",
                    material = analysis?.material?.takeIf { it.isNotBlank() } ?: "Cotton",
                    brand = analysis?.brand ?: "",
                    size = "M",
                    seasons = analysis?.seasons?.takeIf { it.isNotEmpty() } ?: listOf("All-Season"),
                    occasions = analysis?.occasions?.takeIf { it.isNotEmpty() } ?: listOf("Casual"),
                    price = 0.0,
                    notes = analysis?.styleNotes ?: "Captured with Wardrobe Camera",
                    favorite = false,
                    wearCount = 0,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertItem(newItem)
                showToast("Photo saved to your digital wardrobe!")
                onDone()
            } catch (e: Exception) {
                showToast("Could not save garment to wardrobe: ${e.message ?: "Unknown error"}")
            } finally {
                isAnalyzingImage.value = false
                isSavingItemLock.set(false)
            }
        }
    }

    fun deleteOutfit(outfit: Outfit) {
        viewModelScope.launch {
            repository.deleteOutfit(outfit)
            showToast("Outfit deleted")
        }
    }

    fun duplicateOutfit(outfit: Outfit) {
        viewModelScope.launch {
            val copy = outfit.copy(
                id = 0,
                name = "${outfit.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            repository.insertOutfit(copy)
            showToast("Outfit duplicated")
        }
    }

    fun saveOutfit(outfit: Outfit, onDone: () -> Unit) {
        viewModelScope.launch {
            if (outfit.id == 0L || outfit.id == -1L) {
                repository.insertOutfit(outfit.copy(id = 0))
                showToast("Outfit created")
            } else {
                repository.updateOutfit(outfit)
                showToast("Outfit updated")
            }
            onDone()
        }
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
