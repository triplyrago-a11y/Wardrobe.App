package com.example.wardrobe.ui.screens.stylist

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DryCleaning
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.remote.StylistRecommendation
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleChip
import com.example.wardrobe.ui.components.EmptyStateView
import com.example.wardrobe.ui.viewmodel.ClothingChatMessage
import com.example.wardrobe.ui.viewmodel.StylistUiState
import com.example.wardrobe.ui.viewmodel.StylistViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class ClothingPromptSuggestion(
    val categoryLabel: String,
    val question: String,
    val icon: ImageVector
)

private val CLOTHING_PROMPT_SUGGESTIONS = listOf(
    ClothingPromptSuggestion(
        categoryLabel = "Outfit Match",
        question = "What in my closet pairs best together for a sharp everyday outfit?",
        icon = Icons.Outlined.Style
    ),
    ClothingPromptSuggestion(
        categoryLabel = "Color Harmony",
        question = "Which colors in my wardrobe complement navy, olive, and neutral tones?",
        icon = Icons.Outlined.Palette
    ),
    ClothingPromptSuggestion(
        categoryLabel = "Fabric Care",
        question = "How should I wash, dry, and care for wool, linen, and cotton clothes?",
        icon = Icons.Outlined.DryCleaning
    ),
    ClothingPromptSuggestion(
        categoryLabel = "Evening Look",
        question = "Build an elevated dinner or date-night outfit from my clothes.",
        icon = Icons.Outlined.Checkroom
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStylistScreen(
    viewModel: StylistViewModel,
    onNavigateBack: (() -> Unit)? = null,
    isBottomBarVisible: Boolean = false,
    onOpenItemDetail: ((ClothingItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val occasion by viewModel.selectedOccasion.collectAsStateWithLifecycle()
    val weather by viewModel.selectedWeather.collectAsStateWithLifecycle()
    val mood by viewModel.selectedMood.collectAsStateWithLifecycle()
    val buildAroundItem by viewModel.buildAroundItem.collectAsStateWithLifecycle()
    val gapAnalysis by viewModel.gapAnalysis.collectAsStateWithLifecycle()
    val isLoadingGaps by viewModel.isLoadingGaps.collectAsStateWithLifecycle()

    // Dedicated Clothing Chat states
    val hasUserSentQuestion by viewModel.hasUserSentQuestion.collectAsStateWithLifecycle()
    val richChatMessages by viewModel.richChatMessages.collectAsStateWithLifecycle()
    val focusedChatGarment by viewModel.focusedChatGarment.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val colors = AppleTheme.colors
    // Default to 0: Clothing Chat Box! (1: AI Looks, 2: Gap Analysis)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showItemPickerSheet by remember { mutableStateOf(false) }
    var showChatGarmentPickerSheet by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Safe bottom clearance so NO button or input ever overlaps the floating bottom navigation pill
    val navPillClearance: Dp = if (isBottomBarVisible) 100.dp else 16.dp

    LaunchedEffect(Unit) {
        if (gapAnalysis == null) {
            viewModel.performGapAnalysis()
        }
    }

    val isVideoOverlayActive = (selectedTab == 0 && !hasUserSentQuestion)
    val screenBgColor = if (isVideoOverlayActive) Color.Black else colors.bgPrimary
    val headerTextColor = if (isVideoOverlayActive) Color.White else colors.textPrimary
    val headerSubtleBg = if (isVideoOverlayActive) Color.White.copy(alpha = 0.12f) else colors.bgSecondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBgColor)
    ) {
        // =========================================================================
        // LAYER 0 (BOTTOM LAYER): MASCOT VIDEO OVERLAY IN EXACT SAME SIZE (NO SIZE CHANGE)
        // Displayed until the user sends their first clothing question.
        // =========================================================================
        AnimatedVisibility(
            visible = isVideoOverlayActive,
            enter = fadeIn(tween(280)),
            exit = fadeOut(tween(280)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            FixedSizeMascotVideoLayer(
                modifier = Modifier.fillMaxSize()
            )
        }

        // =========================================================================
        // LAYER 1 (TOP LAYER): CHAT BOX UI & STYLIST TABS OVERLAID ON TOP OF VIDEO
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = AppleSpacing.MaxContentWidth)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .zIndex(2f)
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(headerSubtleBg)
                            .testTag("ai_stylist_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = headerTextColor
                        )
                    }
                } else {
                    // Closet Synced Status Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(headerSubtleBg)
                            .border(
                                width = 1.dp,
                                color = colors.accent.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ai_chat_closet_synced, items.size),
                            style = AppleTheme.typography.caption.copy(
                                color = headerTextColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_chat_title),
                        style = AppleTheme.typography.title.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = headerTextColor
                        )
                    )
                }

                // Reset Chat or Regenerate Action Button (48dp touch target)
                IconButton(
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.resetChat()
                        } else {
                            viewModel.generateRecommendations()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(headerSubtleBg)
                        .testTag("ai_header_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.ai_chat_reset),
                        tint = colors.accent
                    )
                }
            }

            // Segmented Mode Tabs: Clothing Chat (Default), AI Looks, Gap Analysis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Clothing Chat", "AI Looks", "Gap Analysis").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val unselectedBg = if (isVideoOverlayActive) {
                        Color(0xFF141822).copy(alpha = 0.82f)
                    } else {
                        colors.bgSecondary
                    }
                    val unselectedText = if (isVideoOverlayActive) {
                        Color.White.copy(alpha = 0.88f)
                    } else {
                        colors.textPrimary
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else unselectedBg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.accent else colors.accent.copy(alpha = 0.24f),
                                shape = CircleShape
                            )
                            .testTag("ai_mode_tab_$index")
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = AppleTheme.typography.caption.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else unselectedText,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // =================================================================
                    // TAB 0: DEDICATED CLOTHING AI CHAT BOX (TOP LAYER OVER VIDEO)
                    // =================================================================
                    ClothingChatBoxTopLayer(
                        items = items,
                        hasUserSentQuestion = hasUserSentQuestion,
                        richChatMessages = richChatMessages,
                        isChatLoading = isChatLoading,
                        focusedChatGarment = focusedChatGarment,
                        chatInput = chatInput,
                        onChatInputChange = { chatInput = it },
                        onSendQuestion = { textToSend ->
                            val finalMsg = textToSend.trim()
                            if (finalMsg.isNotBlank()) {
                                chatInput = ""
                                viewModel.sendChatMessage(finalMsg)
                            }
                        },
                        onSelectFocusGarment = { garment ->
                            viewModel.focusedChatGarment.value = garment
                        },
                        onOpenGarmentPicker = {
                            showChatGarmentPickerSheet = true
                        },
                        onOpenItemDetail = onOpenItemDetail,
                        navPillClearance = navPillClearance,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                1 -> {
                    // =================================================================
                    // TAB 1: CURATED AI OUTFIT RECOMMENDATIONS
                    // =================================================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppleSpacing.ScreenPadding,
                            end = AppleSpacing.ScreenPadding,
                            top = 12.dp,
                            bottom = navPillClearance + 76.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                    ) {
                        item {
                            StylistInputBar(
                                occasion = occasion,
                                weather = weather,
                                mood = mood,
                                buildAroundItem = buildAroundItem,
                                onOccasionChange = { viewModel.selectedOccasion.value = it },
                                onWeatherChange = { viewModel.selectedWeather.value = it },
                                onMoodChange = { viewModel.selectedMood.value = it },
                                onPickItemClick = { showItemPickerSheet = true },
                                onClearItem = { viewModel.buildAroundItem.value = null },
                                onGenerateClick = { viewModel.generateRecommendations() }
                            )
                        }

                        if (actionMessage != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppleSpacing.InputCornerRadius))
                                        .background(colors.accent.copy(alpha = 0.15f))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = actionMessage!!,
                                        style = AppleTheme.typography.caption.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }

                        when (val state = uiState) {
                            is StylistUiState.Loading -> {
                                item { StylistLoadingPulsingState() }
                            }
                            is StylistUiState.Error -> {
                                item {
                                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier.padding(AppleSpacing.Space24),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = colors.warning,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                state.message,
                                                style = AppleTheme.typography.body.copy(color = colors.textPrimary)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            AppleButton(
                                                text = "Retry",
                                                onClick = { viewModel.generateRecommendations() },
                                                isPrimary = true
                                            )
                                        }
                                    }
                                }
                            }
                            is StylistUiState.Success -> {
                                if (state.recommendations.isEmpty()) {
                                    item {
                                        EmptyStateView(
                                            title = "No Looks Found",
                                            subtitle = "Try selecting different parameters or adding more pieces to your closet."
                                        )
                                    }
                                } else {
                                    items(state.recommendations) { rec ->
                                        RecommendationCard(
                                            rec = rec,
                                            onSave = {
                                                viewModel.saveRecommendedOutfit(rec) {
                                                    actionMessage = "Saved to Outfits!"
                                                }
                                            },
                                            onPlan = {
                                                viewModel.planOutfitForToday(rec) {
                                                    actionMessage = "Outfit scheduled for today!"
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            StylistUiState.Idle -> {
                                item {
                                    AppleButton(
                                        text = "Generate 3 Curated Looks",
                                        onClick = { viewModel.generateRecommendations() },
                                        isPrimary = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // =================================================================
                    // TAB 2: CAPSULE WARDROBE GAP ANALYSIS
                    // =================================================================
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppleSpacing.ScreenPadding,
                            end = AppleSpacing.ScreenPadding,
                            top = 12.dp,
                            bottom = navPillClearance + 76.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                    ) {
                        item {
                            Text(
                                text = "Capsule Gap Analysis",
                                style = AppleTheme.typography.title.copy(
                                    fontSize = 22.sp,
                                    color = colors.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gemini analyzes silhouette balance and missing essentials to multiply your outfit potential.",
                                style = AppleTheme.typography.body.copy(
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        if (isLoadingGaps) {
                            item { StylistLoadingPulsingState() }
                        } else if (gapAnalysis != null) {
                            val gap = gapAnalysis!!
                            item {
                                AppleCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(AppleSpacing.Space24)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.accent)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "SMART INSIGHT",
                                                style = AppleTheme.typography.caption.copy(
                                                    color = colors.accent,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = gap.insights,
                                            style = AppleTheme.typography.body.copy(
                                                color = colors.textPrimary,
                                                fontSize = 16.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "+${gap.potentialOutfitsUnlocked} Outfits Unlocked with key additions",
                                            style = AppleTheme.typography.caption.copy(
                                                color = colors.textSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Missing Foundation Staples",
                                    style = AppleTheme.typography.caption.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary
                                    )
                                )
                            }

                            items(gap.missingStaples) { staple ->
                                AppleCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(AppleSpacing.Space16),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(colors.bgPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = staple,
                                            style = AppleTheme.typography.body.copy(
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Closet Piece Picker Sheet for tagging a garment in Clothing Chat
    if (showChatGarmentPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatGarmentPickerSheet = false },
            containerColor = colors.bgPrimary,
            shape = RoundedCornerShape(
                topStart = AppleSpacing.SheetCornerRadius,
                topEnd = AppleSpacing.SheetCornerRadius
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(AppleSpacing.Space24)
            ) {
                Text(
                    text = "Ask About a Piece in Your Closet",
                    style = AppleTheme.typography.title.copy(
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select any clothing item to ask how to style, match, or care for it.",
                    style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = focusedChatGarment?.id == item.id
                        Box(
                            modifier = Modifier
                                .width(114.dp)
                                .height(148.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.cardBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    viewModel.focusedChatGarment.value = item
                                    showChatGarmentPickerSheet = false
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            ) {
                                Column {
                                    Text(
                                        text = item.name,
                                        style = AppleTheme.typography.caption.copy(
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.category,
                                        style = AppleTheme.typography.caption.copy(
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Build Around Item Picker Sheet (for AI Looks tab)
    if (showItemPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showItemPickerSheet = false },
            containerColor = colors.bgPrimary,
            shape = RoundedCornerShape(
                topStart = AppleSpacing.SheetCornerRadius,
                topEnd = AppleSpacing.SheetCornerRadius
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(AppleSpacing.Space24)
            ) {
                Text(
                    text = "Pick Item to Anchor Outfit",
                    style = AppleTheme.typography.title.copy(
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.buildAroundItem.value = item
                                    showItemPickerSheet = false
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = AppleTheme.typography.caption.copy(
                                        color = Color.White,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Fixed-Size Mascot Video Layer (`R.raw.ai_animation_dark`):
 * - Plays the user-provided AI Animation Dark video in its exact native 1440x2160 (2:3) size & aspect ratio
 *   with NO size change, shrinking, stretching, or layout shifting while waiting for the user's message.
 * - Repeats in a continuous loop until the user sends a message.
 * - Uses TextureView + MediaPlayer with a CENTER_INSIDE matrix transform so the video frame
 *   is locked at its exact proportions on the bottom layer while the Chat Box sits on the top layer.
 */
@Composable
private fun FixedSizeMascotVideoLayer(
    modifier: Modifier = Modifier
) {
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("ai_chat_mascot_video_overlay"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Native video dimensions are 1440x2160 (aspect ratio 2:3 = 0.6667f).
        // Locked width & aspect ratio ensures zero size change before the user sends a question.
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1440f / 2160f)
                .align(Alignment.TopCenter),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private fun applyExactSizeMatrix(
                            viewWidth: Int,
                            viewHeight: Int,
                            videoWidth: Int,
                            videoHeight: Int
                        ) {
                            if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
                            val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                            val scaleX: Float
                            val scaleY: Float
                            if (videoRatio > viewRatio) {
                                scaleX = 1f
                                scaleY = viewRatio / videoRatio
                            } else {
                                scaleX = videoRatio / viewRatio
                                scaleY = 1f
                            }
                            val matrix = Matrix()
                            matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
                            setTransform(matrix)
                        }

                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            try {
                                val surface = Surface(surfaceTexture)
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ai_animation_dark}")
                                    setDataSource(ctx, uri)
                                    setVolume(0f, 0f)
                                    isLooping = true
                                    setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                        applyExactSizeMatrix(width, height, vWidth, vHeight)
                                    }
                                    setOnPreparedListener { player ->
                                        applyExactSizeMatrix(
                                            width,
                                            height,
                                            player.videoWidth.takeIf { it > 0 } ?: 1440,
                                            player.videoHeight.takeIf { it > 0 } ?: 2160
                                        )
                                        player.start()
                                    }
                                    setOnCompletionListener { player ->
                                        try {
                                            player.seekTo(0)
                                            player.start()
                                        } catch (_: Exception) {
                                        }
                                    }
                                    setOnErrorListener { _, _, _ -> true }
                                    prepareAsync()
                                }
                                mediaPlayerRef = mp
                            } catch (_: Exception) {
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val mp = mediaPlayerRef ?: return
                            applyExactSizeMatrix(
                                width,
                                height,
                                mp.videoWidth.takeIf { it > 0 } ?: 1440,
                                mp.videoHeight.takeIf { it > 0 } ?: 2160
                            )
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            try {
                                mediaPlayerRef?.stop()
                                mediaPlayerRef?.release()
                            } catch (_: Exception) {
                            }
                            mediaPlayerRef = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}

/**
 * Resized Smaller AI Animation (`R.raw.ai_animation_dark`) specifically for Loading / Searching Answers:
 * - Used ONLY for loading/searching answer indicators (e.g. beside "Searching your wardrobe...",
 *   "Analyzing colors & fabrics...", etc.), keeping the main hero video at its original full size.
 * - Center-crops and focuses on the central glowing diamond mascot inside a compact container,
 *   looping continuously while the answer is being searched/generated.
 */
@Composable
fun MiniAiLoadingMascotVideo(
    size: Dp = 42.dp,
    cornerRadius: Dp = 14.dp,
    zoomScale: Float = 2.45f,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black)
            .border(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.45f),
                shape = RoundedCornerShape(cornerRadius)
            )
            .testTag("mini_ai_loading_mascot_video"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private fun applyMiniMascotCropMatrix(
                            viewWidth: Int,
                            viewHeight: Int,
                            videoWidth: Int,
                            videoHeight: Int
                        ) {
                            if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
                            val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
                            val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                            // Center-crop base scale so the 2:3 video fills the square container,
                            // then multiply by zoomScale so the central AI mascot fills the small badge.
                            val baseScaleX: Float
                            val baseScaleY: Float
                            if (videoRatio > viewRatio) {
                                baseScaleX = videoRatio / viewRatio
                                baseScaleY = 1f
                            } else {
                                baseScaleX = 1f
                                baseScaleY = viewRatio / videoRatio
                            }
                            val matrix = Matrix()
                            matrix.setScale(
                                baseScaleX * zoomScale,
                                baseScaleY * zoomScale,
                                viewWidth / 2f,
                                viewHeight / 2f
                            )
                            setTransform(matrix)
                        }

                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            try {
                                val surface = Surface(surfaceTexture)
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.ai_animation_dark}")
                                    setDataSource(ctx, uri)
                                    setVolume(0f, 0f)
                                    isLooping = true
                                    setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                        applyMiniMascotCropMatrix(width, height, vWidth, vHeight)
                                    }
                                    setOnPreparedListener { player ->
                                        applyMiniMascotCropMatrix(
                                            width,
                                            height,
                                            player.videoWidth.takeIf { it > 0 } ?: 1440,
                                            player.videoHeight.takeIf { it > 0 } ?: 2160
                                        )
                                        player.start()
                                    }
                                    setOnCompletionListener { player ->
                                        try {
                                            player.seekTo(0)
                                            player.start()
                                        } catch (_: Exception) {
                                        }
                                    }
                                    setOnErrorListener { _, _, _ -> true }
                                    prepareAsync()
                                }
                                mediaPlayerRef = mp
                            } catch (_: Exception) {
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val mp = mediaPlayerRef ?: return
                            applyMiniMascotCropMatrix(
                                width,
                                height,
                                mp.videoWidth.takeIf { it > 0 } ?: 1440,
                                mp.videoHeight.takeIf { it > 0 } ?: 2160
                            )
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            try {
                                mediaPlayerRef?.stop()
                                mediaPlayerRef?.release()
                            } catch (_: Exception) {
                            }
                            mediaPlayerRef = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}

/**
 * Compact AI Loading & Searching Answer Bubble:
 * - Displays the resized, smaller looping AI animation (`MiniAiLoadingMascotVideo`) directly
 *   beside the dynamic searching/loading status text while the AI generates its answer.
 */
@Composable
private fun AiLoadingAnswerIndicator(
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    val statusSteps = remember {
        listOf(
            "Searching your wardrobe & pieces...",
            "Matching colors, fabrics & silhouettes...",
            "Styling your personalized answer..."
        )
    }
    var stepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(950L)
            stepIndex = (stepIndex + 1) % statusSteps.size
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bgSecondary)
            .border(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("ai_chat_loading_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Resized smaller AI animation right beside the searching/loading text
        MiniAiLoadingMascotVideo(
            size = 42.dp,
            cornerRadius = 13.dp,
            zoomScale = 2.45f
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Searching & Styling",
                    style = AppleTheme.typography.caption.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusSteps[stepIndex],
                style = AppleTheme.typography.caption.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Top-Layer Clothing Chat Box UI:
 * - Rendered on the top layer above the video until the user sends their first question.
 * - Leaves the upper-center mascot area of the video completely clear while presenting
 *   clothing-focused quick starters, closet piece tagging, and the glowing chat input box below.
 * - Once the user sends a question (`hasUserSentQuestion == true`), displays the full clothing
 *   conversation history with matched closet item cards.
 * - Positions the glowing chat input bar safely above the floating bottom navigation pill.
 */
@Composable
private fun ClothingChatBoxTopLayer(
    items: List<ClothingItem>,
    hasUserSentQuestion: Boolean,
    richChatMessages: List<ClothingChatMessage>,
    isChatLoading: Boolean,
    focusedChatGarment: ClothingItem?,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    onSendQuestion: (String) -> Unit,
    onSelectFocusGarment: (ClothingItem?) -> Unit,
    onOpenGarmentPicker: () -> Unit,
    onOpenItemDetail: ((ClothingItem) -> Unit)?,
    navPillClearance: Dp,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(richChatMessages.size, isChatLoading) {
        val totalItems = richChatMessages.size + (if (isChatLoading) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = AppleSpacing.ScreenPadding,
                end = AppleSpacing.ScreenPadding,
                bottom = navPillClearance
            )
    ) {
        if (!hasUserSentQuestion) {
            // =====================================================================
            // PRE-QUESTION OVERLAY STATE (TOP LAYER OVER THE MASCOT VIDEO)
            // Upper space lets the mascot video shine through unobstructed;
            // Lower dock presents clothing-specific prompt starters & closet pieces.
            // =====================================================================
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0B0E17).copy(alpha = 0.80f))
                    .border(
                        width = 1.dp,
                        color = colors.accent.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("clothing_chat_welcome_overlay")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ask Anything About Your Clothes",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ai_chat_subtitle),
                            style = AppleTheme.typography.caption.copy(
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Closet Quick-Tag Strip ("Tap a garment to ask about it")
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "TAP A CLOSET PIECE TO ASK HOW TO STYLE OR CARE FOR IT:",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items.take(10), key = { it.id }) { garment ->
                            val isTagged = focusedChatGarment?.id == garment.id
                            Row(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isTagged) colors.accent.copy(alpha = 0.28f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        width = if (isTagged) 1.5.dp else 1.dp,
                                        color = if (isTagged) colors.accent else Color.White.copy(alpha = 0.16f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onSelectFocusGarment(if (isTagged) null else garment)
                                    }
                                    .padding(start = 6.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(garment.imageUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = garment.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = garment.name,
                                        style = AppleTheme.typography.caption.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${garment.category} · ${garment.material}",
                                        style = AppleTheme.typography.caption.copy(
                                            color = Color.White.copy(alpha = 0.62f),
                                            fontSize = 9.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Curated Clothing Question Starter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CLOTHING_PROMPT_SUGGESTIONS.forEachIndexed { idx, suggestion ->
                        Box(
                            modifier = Modifier
                                .width(205.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    width = 1.dp,
                                    color = colors.accent.copy(alpha = 0.32f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .testTag("clothing_prompt_chip_$idx")
                                .clickable {
                                    onSendQuestion(suggestion.question)
                                }
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = suggestion.icon,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = suggestion.categoryLabel,
                                        style = AppleTheme.typography.caption.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = suggestion.question,
                                    style = AppleTheme.typography.caption.copy(
                                        color = Color.White.copy(alpha = 0.90f),
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        } else {
            // =====================================================================
            // ACTIVE CONVERSATION STATE (AFTER USER SENDS FIRST QUESTION)
            // =====================================================================
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("clothing_chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(richChatMessages, key = { it.id }) { msg ->
                    ClothingChatMessageBubble(
                        message = msg,
                        onOpenItemDetail = onOpenItemDetail,
                        onAskFollowUpAboutItem = { item ->
                            onSelectFocusGarment(item)
                            onSendQuestion("How should I style my ${item.name} (${item.colors.joinToString("/")} ${item.material}) and what in my closet goes best with it?")
                        }
                    )
                }

                if (isChatLoading) {
                    item(key = "ai_loading_answer_indicator") {
                        AiLoadingAnswerIndicator()
                    }
                }
            }
        }

        // =========================================================================
        // GLOWING APP-THEME BORDER CHAT INPUT BOX (TOP LAYER, SAFELY ABOVE NAV PILL)
        // =========================================================================
        GlowingClothingChatInputBar(
            value = chatInput,
            onValueChange = onChatInputChange,
            focusedGarment = focusedChatGarment,
            onClearFocusedGarment = { onSelectFocusGarment(null) },
            onOpenGarmentPicker = onOpenGarmentPicker,
            onSend = { onSendQuestion(chatInput) },
            isDarkBackdrop = !hasUserSentQuestion || colors.isDark,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp)
        )
    }
}

/**
 * Chat Input Box with Animated App-Theme Color Glow along its border line:
 * - Renders an animated multi-stop sweep/linear gradient in the app's theme colors
 *   (`colors.accent` #2997FF / #0071E3, cyan wave crest #47B0FF, and indigo #5856D6).
 * - Uses native `BlurMaskFilter` + concentric halo strokes in `Modifier.drawBehind` to cast
 *   a genuine luminous app-theme glow around the border line of the chat input.
 */
@Composable
private fun GlowingClothingChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    focusedGarment: ClothingItem?,
    onClearFocusedGarment: () -> Unit,
    onOpenGarmentPicker: () -> Unit,
    onSend: () -> Unit,
    isDarkBackdrop: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    var isFocused by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "chat_input_border_glow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_phase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val primaryThemeColor = colors.accent // #2997FF / #0071E3
    val secondaryCyanGlow = Color(0xFF47B0FF)
    val tertiaryIndigoGlow = Color(0xFF5856D6)
    val cornerRadiusDp = 26.dp

    val containerBg = if (isDarkBackdrop) {
        Color(0xFF121622).copy(alpha = 0.94f)
    } else {
        colors.bgSecondary.copy(alpha = 0.96f)
    }
    val textColor = if (isDarkBackdrop) Color.White else colors.textPrimary
    val placeholderColor = if (isDarkBackdrop) Color.White.copy(alpha = 0.56f) else colors.textSecondary

    Column(modifier = modifier) {
        // Optional Tagged Closet Garment Pill above input line
        AnimatedVisibility(visible = focusedGarment != null) {
            if (focusedGarment != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .background(primaryThemeColor.copy(alpha = 0.18f))
                        .border(1.dp, primaryThemeColor.copy(alpha = 0.65f), CircleShape)
                        .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(focusedGarment.imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = focusedGarment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Asking about: ${focusedGarment.name}",
                        style = AppleTheme.typography.caption.copy(
                            color = if (isDarkBackdrop) Color.White else colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove tagged piece",
                        tint = primaryThemeColor,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClearFocusedGarment() }
                    )
                }
            }
        }

        // Glowing Border Input Line Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .drawBehind {
                    val cornerPx = cornerRadiusDp.toPx()
                    val boost = if (isFocused || value.isNotBlank()) 1.22f else 1.0f
                    val pulseAlpha = (glowPulse * boost).coerceIn(0.55f, 1f)

                    // 1. Native Android BlurMaskFilter outer bloom glow in app theme color
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 8.dp.toPx()
                            color = primaryThemeColor
                                .copy(alpha = 0.48f * pulseAlpha)
                                .toArgb()
                            maskFilter = BlurMaskFilter(
                                12.dp.toPx(),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            cornerPx,
                            cornerPx,
                            paint
                        )
                    }

                    // 2. Animated Multi-Layer App-Theme Gradient Glow Halo + Crisp Border Line
                    val dx = cos(glowPhase) * (size.width * 0.45f)
                    val dy = sin(glowPhase) * (size.height * 0.45f)
                    val center = Offset(size.width / 2f, size.height / 2f)

                    val animatedBorderBrush = Brush.linearGradient(
                        colors = listOf(
                            primaryThemeColor,
                            secondaryCyanGlow,
                            tertiaryIndigoGlow,
                            secondaryCyanGlow,
                            primaryThemeColor
                        ),
                        start = Offset(center.x - dx, center.y - dy),
                        end = Offset(center.x + dx, center.y + dy)
                    )

                    // Soft outer glow stroke
                    drawRoundRect(
                        brush = animatedBorderBrush,
                        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                        size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(cornerPx + 2.dp.toPx(), cornerPx + 2.dp.toPx()),
                        alpha = 0.34f * pulseAlpha,
                        style = Stroke(width = 5.5.dp.toPx())
                    )

                    // Mid glow stroke
                    drawRoundRect(
                        brush = animatedBorderBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = 0.62f * pulseAlpha,
                        style = Stroke(width = 3.2.dp.toPx())
                    )

                    // Crisp luminous inner border line
                    drawRoundRect(
                        brush = animatedBorderBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = 0.98f,
                        style = Stroke(width = if (isFocused) 2.2.dp.toPx() else 1.8.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(cornerRadiusDp))
                .background(containerBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("glowing_chat_input_container")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button to tag a piece from the closet
                IconButton(
                    onClick = onOpenGarmentPicker,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(primaryThemeColor.copy(alpha = 0.14f))
                        .testTag("chat_attach_garment_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checkroom,
                        contentDescription = stringResource(R.string.ai_chat_attach_garment),
                        tint = primaryThemeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Input Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .onFocusChanged { isFocused = it.isFocused }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ai_chat_input_placeholder),
                            style = AppleTheme.typography.body.copy(
                                color = placeholderColor,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_input"),
                        textStyle = AppleTheme.typography.body.copy(
                            color = textColor,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(primaryThemeColor),
                        singleLine = false,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() })
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button (48dp touch target)
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(primaryThemeColor, secondaryCyanGlow)
                            )
                        )
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ai_chat_send),
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClothingChatMessageBubble(
    message: ClothingChatMessage,
    onOpenItemDetail: ((ClothingItem) -> Unit)?,
    onAskFollowUpAboutItem: (ClothingItem) -> Unit
) {
    val colors = AppleTheme.colors
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // If user attached a closet piece to this question, show its badge
        if (isUser && message.taggedItem != null) {
            val tagged = message.taggedItem
            Row(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.16f))
                    .border(1.dp, colors.accent.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(tagged.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = tagged.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tagged.name,
                    style = AppleTheme.typography.caption.copy(
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Message Bubble
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(if (isUser) colors.accent else colors.bgSecondary)
                .border(
                    width = if (isUser) 0.dp else 1.dp,
                    color = if (isUser) Color.Transparent else colors.cardBorder,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                text = message.text.replace("**", ""),
                style = AppleTheme.typography.body.copy(
                    color = if (isUser) Color.White else colors.textPrimary,
                    fontSize = 15.sp
                )
            )
        }

        // If AI response matched items from the user's wardrobe, display interactive mini closet cards
        if (!isUser && message.referencedItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(message.referencedItems, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgSecondary)
                            .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (onOpenItemDetail != null) {
                                    onOpenItemDetail(item)
                                } else {
                                    onAskFollowUpAboutItem(item)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.name,
                                style = AppleTheme.typography.caption.copy(
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.category} · ${item.material}",
                                style = AppleTheme.typography.caption.copy(
                                    color = colors.textSecondary,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StylistInputBar(
    occasion: String,
    weather: String,
    mood: String,
    buildAroundItem: ClothingItem?,
    onOccasionChange: (String) -> Unit,
    onWeatherChange: (String) -> Unit,
    onMoodChange: (String) -> Unit,
    onPickItemClick: () -> Unit,
    onClearItem: () -> Unit,
    onGenerateClick: () -> Unit
) {
    val colors = AppleTheme.colors

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppleSpacing.Space16)) {
            Text(
                text = "Occasion",
                style = AppleTheme.typography.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Casual", "Work", "Date Night", "Travel", "Formal").forEach { occ ->
                    AppleChip(
                        text = occ,
                        selected = occasion == occ,
                        onClick = { onOccasionChange(occ) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Text(
                text = "Weather Forecast",
                style = AppleTheme.typography.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sunny (72°F)", "Cool (55°F)", "Rainy / Cold (42°F)", "Warm (82°F)").forEach { w ->
                    AppleChip(
                        text = w,
                        selected = weather == w,
                        onClick = { onWeatherChange(w) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Text(
                text = "Styling Mood",
                style = AppleTheme.typography.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Minimalist", "Monochrome", "Parisian Chic", "Bold", "Cozy Layered").forEach { m ->
                    AppleChip(
                        text = m,
                        selected = mood == m,
                        onClick = { onMoodChange(m) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Build Around Piece",
                    style = AppleTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                )

                if (buildAroundItem != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildAroundItem.name.take(16),
                            style = AppleTheme.typography.caption.copy(
                                color = colors.accent,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        IconButton(onClick = onClearItem, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Pick item (optional)",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPickItemClick() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            AppleButton(
                text = "Generate Curated Looks",
                onClick = onGenerateClick,
                isPrimary = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "generate_looks_button"
            )
        }
    }
}

@Composable
fun StylistLoadingPulsingState() {
    val colors = AppleTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpacing.Space32),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.bgPrimary)
                    .border(1.dp, colors.accent.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniAiLoadingMascotVideo(
                    size = 48.dp,
                    cornerRadius = 15.dp,
                    zoomScale = 2.45f,
                    modifier = Modifier.scale(scale)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Searching & Curating Looks...",
                        style = AppleTheme.typography.title.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Balancing proportions, textures & weather harmony.",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(
    rec: StylistRecommendation,
    onSave: () -> Unit,
    onPlan: () -> Unit
) {
    val colors = AppleTheme.colors

    AppleCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppleSpacing.Space24)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rec.title,
                    style = AppleTheme.typography.title.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.bgPrimary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rec.occasion,
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rec.items.take(4).forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                            .background(colors.bgPrimary)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Text(
                text = rec.explanation,
                style = AppleTheme.typography.body.copy(
                    color = colors.textPrimary,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stylist Tip: ",
                    style = AppleTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                )
                Text(
                    text = rec.stylingTip,
                    style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppleButton(
                    text = "Save Outfit",
                    onClick = onSave,
                    isPrimary = false,
                    leadingIcon = {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                AppleButton(
                    text = "Plan for Today",
                    onClick = onPlan,
                    isPrimary = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}
