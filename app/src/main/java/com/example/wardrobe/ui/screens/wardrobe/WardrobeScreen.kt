package com.example.wardrobe.ui.screens.wardrobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleChip
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.components.AppleInputField
import com.example.wardrobe.ui.components.EmptyStateView
import com.example.wardrobe.ui.viewmodel.FilterState
import com.example.wardrobe.ui.viewmodel.SortMode
import com.example.wardrobe.ui.viewmodel.WardrobeViewModel

val WARDROBE_CATEGORIES = listOf(
    "All", "Tops", "Bottoms", "Dresses", "Outerwear", "Shoes", "Accessories"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    viewModel: WardrobeViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToCameraCapture: () -> Unit = onNavigateToAdd,
    onOpenItemDetail: (ClothingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val allRawItems by viewModel.allItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedItemIds.collectAsStateWithLifecycle()
    val colors = AppleTheme.colors

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddSourceSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        val columns = if (maxWidth > 600.dp) 3 else 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = AppleSpacing.MaxContentWidth)
                .align(Alignment.TopCenter)
        ) {
            // Top Bar with Title and Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelect) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearMultiSelect() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                        Text(
                            text = "${selectedIds.size} selected",
                            style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                        )
                    }

                    Row {
                        IconButton(onClick = { viewModel.bulkDeleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = colors.destructive)
                        }
                    }
                } else {
                    Text(
                        text = "Wardrobe",
                        style = AppleTheme.typography.title.copy(color = colors.textPrimary)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sort Menu Button
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = colors.textPrimary)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Filter Button
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (filters.favoritesOnly || filters.season != "All" || filters.occasion != "All") colors.accent else colors.textPrimary
                            )
                        }
                    }
                }
            }

            // Search Bar
            AppleInputField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search garments, fabrics, brands...",
                modifier = Modifier.padding(horizontal = AppleSpacing.ScreenPadding),
                testTag = "wardrobe_search_input"
            )

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
            ) {
                WARDROBE_CATEGORIES.forEach { category ->
                    AppleChip(
                        text = category,
                        selected = filters.category.equals(category, ignoreCase = true),
                        onClick = { viewModel.setCategoryFilter(category) },
                        testTag = "category_chip_$category"
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            // 2-Column (or 3-column on wide screens) Grid of Garments
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (allRawItems.isEmpty()) {
                        EmptyStateView(
                            title = "Your Wardrobe is Empty",
                            subtitle = "Take photos or upload items to begin building your curated collection.",
                            actionText = "Add First Garment",
                            onActionClick = onNavigateToAdd
                        )
                    } else {
                        EmptyStateView(
                            title = "No Matches Found",
                            subtitle = "Try adjusting your filters, category, or search term.",
                            actionText = "Reset Filters",
                            onActionClick = {
                                viewModel.setFilters(FilterState())
                                viewModel.setSearchQuery("")
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppleSpacing.ScreenPadding,
                        end = AppleSpacing.ScreenPadding,
                        top = 8.dp,
                        bottom = 180.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16),
                    verticalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    items(items, key = { it.id }) { item ->
                        WardrobeGridCard(
                            item = item,
                            isSelected = selectedIds.contains(item.id),
                            isMultiSelect = isMultiSelect,
                            onClick = {
                                if (isMultiSelect) {
                                    viewModel.toggleMultiSelect(item)
                                } else {
                                    onOpenItemDetail(item)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleMultiSelect(item)
                            },
                            onFavoriteToggle = {
                                viewModel.toggleFavorite(item)
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add Garment (raised above nav pill so zero buttons overlap)
        FloatingActionButton(
            onClick = { showAddSourceSheet = true },
            containerColor = colors.accent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 104.dp)
                .shadow(12.dp, CircleShape)
                .testTag("add_item_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Item",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Add Source Sheet: Take Photo or Upload
    if (showAddSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSourceSheet = false },
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
                    text = "Add to Wardrobe",
                    style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(AppleSpacing.Space16))
                AppleButton(
                    text = "Take Photo (Camera)",
                    onClick = {
                        showAddSourceSheet = false
                        onNavigateToCameraCapture()
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "sheet_take_photo_button"
                )
                Spacer(modifier = Modifier.height(AppleSpacing.Space12))
                AppleButton(
                    text = "Upload from Photos",
                    onClick = {
                        showAddSourceSheet = false
                        onNavigateToAdd()
                    },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "sheet_upload_photos_button"
                )
                Spacer(modifier = Modifier.height(AppleSpacing.Space24))
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        WardrobeFilterSheet(
            currentFilters = filters,
            onApply = { newFilters ->
                viewModel.setFilters(newFilters)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WardrobeGridCard(
    item: ClothingItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors

    AppleCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wardrobe_card_${item.id}"),
        onClick = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            // Square Thumbnail
            val isTransparentCutout = item.imageUri.endsWith(".png", ignoreCase = true)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(colors.bgSecondary)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = if (isTransparentCutout) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTransparentCutout) 10.dp else 0.dp)
                )

                // Category Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.category,
                        style = AppleTheme.typography.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                }

                // Favorite Heart Toggle
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.40f))
                ) {
                    Icon(
                        imageVector = if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.favorite) Color(0xFFFF2D55) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Multi-select Checkmark
                if (isMultiSelect) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else Color.Black.copy(alpha = 0.5f))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Info Section: 1-line ellipsized name and brand/wears
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.name,
                    style = AppleTheme.typography.body.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.brand.ifBlank { item.material },
                        style = AppleTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (item.price > 0) "₹${item.price.toInt()} · ${item.wearCount}w" else "${item.wearCount}w",
                        style = AppleTheme.typography.caption.copy(
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeFilterSheet(
    currentFilters: FilterState,
    onApply: (FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var favOnly by remember { mutableStateOf(currentFilters.favoritesOnly) }
    var selectedSeason by remember { mutableStateOf(currentFilters.season) }
    var selectedOccasion by remember { mutableStateOf(currentFilters.occasion) }
    val colors = AppleTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    text = "Filters",
                    style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                )
                Text(
                    text = "Reset",
                    style = AppleTheme.typography.body.copy(color = colors.accent, fontWeight = FontWeight.Medium),
                    modifier = Modifier.clickable {
                        favOnly = false
                        selectedSeason = "All"
                        selectedOccasion = "All"
                    }
                )
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

            // Favorites Only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { favOnly = !favOnly },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Favorites Only",
                    style = AppleTheme.typography.body.copy(color = colors.textPrimary)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (favOnly) colors.accent else colors.bgSecondary)
                        .border(1.dp, colors.cardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (favOnly) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

            // Season
            Text(
                text = "Season",
                style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Spring", "Summer", "Fall", "Winter", "All-Season").forEach { season ->
                    AppleChip(
                        text = season,
                        selected = selectedSeason == season,
                        onClick = { selectedSeason = season }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

            // Occasion
            Text(
                text = "Occasion",
                style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Casual", "Work", "Formal", "Party", "Sport", "Loungewear").forEach { occ ->
                    AppleChip(
                        text = occ,
                        selected = selectedOccasion == occ,
                        onClick = { selectedOccasion = occ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space48))

            AppleButton(
                text = "Apply Filters",
                onClick = {
                    onApply(
                        currentFilters.copy(
                            favoritesOnly = favOnly,
                            season = selectedSeason,
                            occasion = selectedOccasion
                        )
                    )
                },
                isPrimary = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))
        }
    }
}
