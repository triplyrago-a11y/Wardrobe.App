package com.example.wardrobe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WardrobeApplication
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.remote.StylistRecommendation
import com.example.wardrobe.data.remote.WardrobeGapAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface StylistUiState {
    object Idle : StylistUiState
    object Loading : StylistUiState
    data class Success(val recommendations: List<StylistRecommendation>) : StylistUiState
    data class Error(val message: String) : StylistUiState
}

data class ClothingChatMessage(
    val id: Long = System.nanoTime(),
    val role: String, // "user" or "model"
    val text: String,
    val taggedItem: ClothingItem? = null,
    val referencedItems: List<ClothingItem> = emptyList()
)

class StylistViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WardrobeApplication).repository

    val allItems: StateFlow<List<ClothingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stylist Input parameters
    val selectedOccasion = MutableStateFlow("Casual")
    val selectedWeather = MutableStateFlow("Sunny & Mild (70°F)")
    val selectedMood = MutableStateFlow("Minimalist")
    val buildAroundItem = MutableStateFlow<ClothingItem?>(null)

    // Recommendations State
    private val _uiState = MutableStateFlow<StylistUiState>(StylistUiState.Idle)
    val uiState: StateFlow<StylistUiState> = _uiState.asStateFlow()

    // Gap Analysis State
    val gapAnalysis = MutableStateFlow<WardrobeGapAnalysis?>(null)
    val isLoadingGaps = MutableStateFlow(false)

    // Clothing Chat State:
    // Until user sends a question, hasUserSentQuestion is false and the mascot video overlay is shown in its exact size
    private val _hasUserSentQuestion = MutableStateFlow(false)
    val hasUserSentQuestion: StateFlow<Boolean> = _hasUserSentQuestion.asStateFlow()

    val focusedChatGarment = MutableStateFlow<ClothingItem?>(null)

    private val _richChatMessages = MutableStateFlow<List<ClothingChatMessage>>(emptyList())
    val richChatMessages: StateFlow<List<ClothingChatMessage>> = _richChatMessages.asStateFlow()

    // Backward-compatible Pair list
    val chatMessages = MutableStateFlow<List<Pair<String, String>>>(
        listOf("model" to "Ask me anything about your clothes—outfit pairings, color matching, fabric washing care, or styling advice for any piece in your wardrobe.")
    )
    val isChatLoading = MutableStateFlow(false)

    init {
        // Initial auto-recommendation on first open
        viewModelScope.launch {
            val items = allItems.first()
            if (items.isNotEmpty()) {
                generateRecommendations()
            }
        }
    }

    fun generateRecommendations() {
        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            _uiState.value = StylistUiState.Loading
            val items = allItems.first()
            val result = repository.geminiService.getStylistRecommendations(
                wardrobe = items,
                occasion = selectedOccasion.value,
                weather = selectedWeather.value,
                mood = selectedMood.value,
                buildAroundItem = buildAroundItem.value
            )
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 1600L) {
                kotlinx.coroutines.delay(1600L - elapsed)
            }

            result.onSuccess { recs ->
                _uiState.value = StylistUiState.Success(recs)
            }.onFailure { e ->
                _uiState.value = StylistUiState.Error(e.message ?: "Failed to generate recommendations")
            }
        }
    }

    fun performGapAnalysis() {
        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            isLoadingGaps.value = true
            val items = allItems.first()
            val result = repository.geminiService.analyzeWardrobeGaps(items)
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 1600L) {
                kotlinx.coroutines.delay(1600L - elapsed)
            }
            result.onSuccess {
                gapAnalysis.value = it
            }
            isLoadingGaps.value = false
        }
    }

    fun sendChatMessage(message: String, attachedGarment: ClothingItem? = focusedChatGarment.value) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        // Mark that the user has sent their question so the video overlay transitions to the conversation
        _hasUserSentQuestion.value = true

        val userRichMsg = ClothingChatMessage(
            role = "user",
            text = trimmed,
            taggedItem = attachedGarment
        )
        _richChatMessages.value = _richChatMessages.value + userRichMsg

        val currentPairs = chatMessages.value.toMutableList()
        currentPairs.add("user" to trimmed)
        chatMessages.value = currentPairs

        // Clear attached garment after sending
        focusedChatGarment.value = null

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            isChatLoading.value = true
            val items = allItems.first()
            val result = repository.geminiService.chatWithStylist(
                conversation = currentPairs,
                wardrobe = items,
                userMessage = trimmed,
                focusedGarment = attachedGarment
            )
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 2200L) {
                kotlinx.coroutines.delay(2200L - elapsed)
            }
            result.onSuccess { reply ->
                val matchedItems = findReferencedClosetItems(reply, trimmed, items, attachedGarment)
                val modelRichMsg = ClothingChatMessage(
                    role = "model",
                    text = reply,
                    referencedItems = matchedItems
                )
                _richChatMessages.value = _richChatMessages.value + modelRichMsg

                val updated = chatMessages.value.toMutableList()
                updated.add("model" to reply)
                chatMessages.value = updated
            }.onFailure {
                val fallback = "A capsule wardrobe shines brightest with monochrome harmony, balanced proportions, and well-cared-for natural fabrics."
                val modelRichMsg = ClothingChatMessage(
                    role = "model",
                    text = fallback
                )
                _richChatMessages.value = _richChatMessages.value + modelRichMsg

                val updated = chatMessages.value.toMutableList()
                updated.add("model" to fallback)
                chatMessages.value = updated
            }
            isChatLoading.value = false
        }
    }

    fun resetChat() {
        _hasUserSentQuestion.value = false
        _richChatMessages.value = emptyList()
        focusedChatGarment.value = null
        chatMessages.value = listOf(
            "model" to "Ask me anything about your clothes—outfit pairings, color matching, fabric washing care, or styling advice for any piece in your wardrobe."
        )
    }

    private fun findReferencedClosetItems(
        reply: String,
        question: String,
        wardrobe: List<ClothingItem>,
        attachedGarment: ClothingItem?
    ): List<ClothingItem> {
        if (wardrobe.isEmpty()) return emptyList()
        val combined = "$reply $question".lowercase()
        val matched = wardrobe.filter { item ->
            item.id == attachedGarment?.id ||
                (item.name.length >= 3 && combined.contains(item.name.lowercase()))
        }.distinctBy { it.id }.take(4)

        return if (matched.isNotEmpty()) {
            matched
        } else {
            wardrobe.take(3)
        }
    }

    fun saveRecommendedOutfit(recommendation: StylistRecommendation, onDone: () -> Unit) {
        viewModelScope.launch {
            val outfit = Outfit(
                name = recommendation.title,
                itemIds = recommendation.itemIds,
                occasion = recommendation.occasion,
                season = "All-Season",
                favorite = false
            )
            repository.insertOutfit(outfit)
            onDone()
        }
    }

    fun planOutfitForToday(recommendation: StylistRecommendation, onDone: () -> Unit) {
        viewModelScope.launch {
            val outfit = Outfit(
                name = recommendation.title,
                itemIds = recommendation.itemIds,
                occasion = recommendation.occasion,
                season = "All-Season",
                favorite = false
            )
            val id = repository.insertOutfit(outfit)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            repository.planOutfitForDate(todayStr, id)
            onDone()
        }
    }
}
