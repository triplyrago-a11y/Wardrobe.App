package com.example.wardrobe.ui.screens.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleConfirmationDialog
import com.example.wardrobe.ui.components.EmptyStateView
import com.example.wardrobe.ui.viewmodel.WardrobeViewModel

@Composable
fun OutfitsScreen(
    viewModel: WardrobeViewModel,
    onNavigateToCanvasBuilder: (Outfit?) -> Unit,
    onNavigateToStylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outfits by viewModel.allOutfits.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val colors = AppleTheme.colors

    var outfitToDelete by remember { mutableStateOf<Outfit?>(null) }

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
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Outfits",
                    style = AppleTheme.typography.title.copy(color = colors.textPrimary)
                )

                // Sparkle AI shortcut
                IconButton(onClick = onNavigateToStylist) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Stylist",
                        tint = colors.accent
                    )
                }
            }

            if (outfits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "No Saved Outfits",
                        subtitle = "Build your first capsule look with the interactive canvas or ask the AI Stylist.",
                        actionText = "Create New Outfit",
                        onActionClick = { onNavigateToCanvasBuilder(null) }
                    )
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
                    items(outfits, key = { it.id }) { outfit ->
                        OutfitGridCard(
                            outfit = outfit,
                            allItems = allItems,
                            onEdit = { onNavigateToCanvasBuilder(outfit) },
                            onDuplicate = { viewModel.duplicateOutfit(outfit) },
                            onDelete = { outfitToDelete = outfit },
                            onToggleFavorite = { viewModel.toggleFavoriteOutfit(outfit) }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Build Outfit (raised above nav pill so zero buttons overlap)
        FloatingActionButton(
            onClick = { onNavigateToCanvasBuilder(null) },
            containerColor = colors.accent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 104.dp)
                .shadow(12.dp, CircleShape)
                .testTag("new_outfit_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Outfit",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (outfitToDelete != null) {
        AppleConfirmationDialog(
            title = "Delete Outfit",
            message = "Are you sure you want to delete '${outfitToDelete!!.name}'?",
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                outfitToDelete?.let { viewModel.deleteOutfit(it) }
                outfitToDelete = null
            },
            onDismiss = { outfitToDelete = null }
        )
    }
}

@Composable
fun OutfitGridCard(
    outfit: Outfit,
    allItems: List<ClothingItem>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val outfitGarments = remember(outfit, allItems) {
        val map = allItems.associateBy { it.id }
        outfit.itemIds.mapNotNull { map[it] }
    }

    AppleCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("outfit_card_${outfit.id}"),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collage Preview (2x2 square mini-grid)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(colors.bgSecondary)
            ) {
                if (outfitGarments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pieces", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                    }
                } else if (outfitGarments.size == 1) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(outfitGarments[0].imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = outfit.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 2x2 grid preview
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(outfitGarments[0].imageUri).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (outfitGarments.size > 1) {
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(outfitGarments[1].imageUri).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        if (outfitGarments.size > 2) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(outfitGarments[2].imageUri).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    if (outfitGarments.size > 3) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current).data(outfitGarments[3].imageUri).crossfade(true).build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(colors.bgPrimary.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+${outfitGarments.size - 2}", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Favorite Heart Toggle
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.40f))
                ) {
                    Icon(
                        imageVector = if (outfit.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (outfit.favorite) Color(0xFFFF2D55) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Occasion Tag
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.60f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = outfit.occasion,
                        style = AppleTheme.typography.caption.copy(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }

            // Bottom details row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = outfit.name,
                        style = AppleTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${outfit.itemIds.size} pieces · ${outfit.season}",
                        style = AppleTheme.typography.caption.copy(fontSize = 12.sp, color = colors.textSecondary),
                        maxLines = 1
                    )
                }

                // Context Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Canvas") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = colors.destructive) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
