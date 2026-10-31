package com.example.wardrobe.ui.screens.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.CanvasItemPosition
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleChip
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.components.AppleInputField
import com.example.wardrobe.ui.screens.wardrobe.WARDROBE_CATEGORIES
import kotlin.math.roundToInt

data class CanvasItemState(
    val item: ClothingItem,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1.0f,
    var zIndex: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitCanvasBuilder(
    allWardrobeItems: List<ClothingItem>,
    existingOutfit: Outfit? = null,
    onSaveOutfit: (Outfit) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors

    // Items active on the canvas
    val canvasItems = remember { mutableStateMapOf<Long, CanvasItemState>() }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var showSaveSheet by remember { mutableStateOf(false) }

    var outfitName by remember { mutableStateOf(existingOutfit?.name ?: "Curated Ensemble") }
    var outfitOccasion by remember { mutableStateOf(existingOutfit?.occasion ?: "Casual") }
    var outfitSeason by remember { mutableStateOf(existingOutfit?.season ?: "All-Season") }

    // Pre-populate if editing an existing outfit
    remember(existingOutfit, allWardrobeItems) {
        if (existingOutfit != null && canvasItems.isEmpty()) {
            val itemMap = allWardrobeItems.associateBy { it.id }
            existingOutfit.itemIds.forEachIndexed { index, id ->
                val garment = itemMap[id]
                if (garment != null) {
                    canvasItems[id] = CanvasItemState(
                        item = garment,
                        offsetX = (index % 2) * 120f,
                        offsetY = (index / 2) * 140f,
                        scale = 1.0f,
                        zIndex = index
                    )
                }
            }
        }
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
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }

                Text(
                    text = "Canvas Builder",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                )

                AppleButton(
                    text = "Save",
                    onClick = { showSaveSheet = true },
                    isPrimary = true,
                    enabled = canvasItems.isNotEmpty(),
                    modifier = Modifier.height(36.dp),
                    testTag = "save_canvas_outfit"
                )
            }

            // Interactive Layout Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AppleSpacing.ScreenPadding)
                    .clip(RoundedCornerShape(AppleSpacing.CardCornerRadius))
                    .background(colors.bgSecondary)
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(AppleSpacing.CardCornerRadius))
                    .pointerInput(Unit) {
                        // Click on background deselects
                    }
            ) {
                val canvasW = maxWidth
                val canvasH = maxHeight

                if (canvasItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap pieces below to add to canvas",
                            style = AppleTheme.typography.body.copy(color = colors.textSecondary)
                        )
                    }
                } else {
                    // Render garments sorted by zIndex
                    canvasItems.values.sortedBy { it.zIndex }.forEach { canvasItem ->
                        val isSelected = selectedItemId == canvasItem.item.id

                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(canvasItem.offsetX.roundToInt(), canvasItem.offsetY.roundToInt())
                                }
                                .size(130.dp)
                                .scale(canvasItem.scale)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.5.dp,
                                    color = if (isSelected) colors.accent else Color.Black.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .background(Color.White)
                                .pointerInput(canvasItem.item.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        selectedItemId = canvasItem.item.id
                                        canvasItem.offsetX += dragAmount.x
                                        canvasItem.offsetY += dragAmount.y
                                    }
                                }
                                .clickable {
                                    selectedItemId = canvasItem.item.id
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(canvasItem.item.imageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = canvasItem.item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Remove item badge
                            if (isSelected) {
                                IconButton(
                                    onClick = {
                                        canvasItems.remove(canvasItem.item.id)
                                        selectedItemId = null
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(colors.destructive, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Controls when a garment is selected (Layering & Scale)
                val selected = selectedItemId?.let { canvasItems[it] }
                if (selected != null) {
                    AppleGlassSurface(
                        shape = CircleShape,
                        elevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Scale down
                            IconButton(
                                onClick = {
                                    selected.scale = (selected.scale - 0.1f).coerceAtLeast(0.5f)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Smaller", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = "${(selected.scale * 100).toInt()}%",
                                style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary)
                            )

                            // Scale up
                            IconButton(
                                onClick = {
                                    selected.scale = (selected.scale + 0.1f).coerceAtMost(1.8f)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Larger", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }

                            Box(modifier = Modifier.width(1.dp).height(20.dp).background(colors.cardBorder))

                            // Bring to front
                            IconButton(
                                onClick = {
                                    val maxZ = (canvasItems.values.maxOfOrNull { it.zIndex } ?: 0) + 1
                                    selected.zIndex = maxZ
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FlipToFront, contentDescription = "Front", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }

                            // Send to back
                            IconButton(
                                onClick = {
                                    val minZ = (canvasItems.values.minOfOrNull { it.zIndex } ?: 0) - 1
                                    selected.zIndex = minZ
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FlipToBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space12))

            // Carousel Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
            ) {
                WARDROBE_CATEGORIES.forEach { cat ->
                    AppleChip(
                        text = cat,
                        selected = selectedCategory.equals(cat, ignoreCase = true),
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space8))

            // Bottom Garment Picker Carousel
            val filteredForCarousel = remember(selectedCategory, allWardrobeItems) {
                if (selectedCategory == "All") allWardrobeItems
                else allWardrobeItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space12)
            ) {
                items(filteredForCarousel, key = { it.id }) { item ->
                    val isOnCanvas = canvasItems.containsKey(item.id)

                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(AppleSpacing.InputCornerRadius))
                            .border(
                                width = if (isOnCanvas) 2.dp else 1.dp,
                                color = if (isOnCanvas) colors.accent else colors.cardBorder,
                                shape = RoundedCornerShape(AppleSpacing.InputCornerRadius)
                            )
                            .background(colors.bgSecondary)
                            .clickable {
                                if (isOnCanvas) {
                                    canvasItems.remove(item.id)
                                    if (selectedItemId == item.id) selectedItemId = null
                                } else {
                                    val nextZ = (canvasItems.values.maxOfOrNull { it.zIndex } ?: 0) + 1
                                    canvasItems[item.id] = CanvasItemState(
                                        item = item,
                                        offsetX = 50f + (canvasItems.size * 20f),
                                        offsetY = 50f + (canvasItems.size * 20f),
                                        zIndex = nextZ
                                    )
                                    selectedItemId = item.id
                                }
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

                        if (isOnCanvas) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Save Outfit Bottom Sheet
    if (showSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSaveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.bgPrimary,
            shape = RoundedCornerShape(topStart = AppleSpacing.SheetCornerRadius, topEnd = AppleSpacing.SheetCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppleSpacing.Space24)
            ) {
                Text(
                    text = "Save Outfit",
                    style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                )

                Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                AppleInputField(
                    value = outfitName,
                    onValueChange = { outfitName = it },
                    label = "Outfit Name",
                    placeholder = "e.g. Modern Monochrome Weekend"
                )

                Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                Text(
                    text = "Occasion",
                    style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Casual", "Work", "Formal", "Party", "Travel", "Loungewear").forEach { occ ->
                        AppleChip(
                            text = occ,
                            selected = outfitOccasion == occ,
                            onClick = { outfitOccasion = occ }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                Text(
                    text = "Season",
                    style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All-Season", "Spring", "Summer", "Fall", "Winter").forEach { season ->
                        AppleChip(
                            text = season,
                            selected = outfitSeason == season,
                            onClick = { outfitSeason = season }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space32))

                AppleButton(
                    text = "Confirm & Save Outfit",
                    onClick = {
                        val ids = canvasItems.keys.toList()
                        val newOutfit = Outfit(
                            id = existingOutfit?.id ?: 0,
                            name = outfitName.ifBlank { "Curated Look" },
                            itemIds = ids,
                            occasion = outfitOccasion,
                            season = outfitSeason,
                            favorite = existingOutfit?.favorite ?: false,
                            createdAt = existingOutfit?.createdAt ?: System.currentTimeMillis()
                        )
                        showSaveSheet = false
                        onSaveOutfit(newOutfit)
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(AppleSpacing.Space16))
            }
        }
    }
}
