package com.example.wardrobe.ui.screens.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.components.EmptyStateView
import com.example.wardrobe.ui.viewmodel.WardrobeViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: WardrobeViewModel,
    userName: String,
    onNavigateToAdd: () -> Unit,
    onNavigateToStylist: () -> Unit,
    onOpenItemDetail: (ClothingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val outfits by viewModel.allOutfits.collectAsStateWithLifecycle()
    val todayOutfit by viewModel.todayOutfit.collectAsStateWithLifecycle()
    val colors = AppleTheme.colors

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val mostWornItem = items.maxByOrNull { it.wearCount }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = AppleSpacing.MaxContentWidth),
            contentPadding = PaddingValues(
                start = AppleSpacing.ScreenPadding,
                end = AppleSpacing.ScreenPadding,
                top = 28.dp,
                bottom = 180.dp // Extra clearance above floating nav pill + FAB
            ),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.GroupSpacing)
        ) {
            // Header: Greeting & Subtitle
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.wardrobe.ui.components.WardrobeLogo(
                                mode = com.example.wardrobe.ui.components.LogoMode.Symbol,
                                size = 28.dp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "$greeting,",
                                    style = AppleTheme.typography.title.copy(
                                        color = colors.textSecondary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = userName.ifBlank { "Alex" },
                                    style = AppleTheme.typography.title.copy(
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }

                        // Weather pill with subtle icon
                        AppleGlassSurface(
                            shape = CircleShape,
                            elevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "72°F · Mild Layers",
                                    style = AppleTheme.typography.caption.copy(
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Today's Outfit Hero Card
            item {
                if (todayOutfit != null) {
                    TodayOutfitHeroCard(
                        outfit = todayOutfit!!,
                        allItems = items,
                        onWearThis = { viewModel.wearTodayOutfit(todayOutfit!!) },
                        onRegenerate = { viewModel.regenerateTodayOutfit() }
                    )
                } else {
                    EmptyStateView(
                        title = "No Outfits Yet",
                        subtitle = "Add pieces to your wardrobe to get AI-powered outfit recommendations.",
                        actionText = "Add Your First Item",
                        onActionClick = onNavigateToAdd
                    )
                }
            }

            // Quick Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    StatCard(
                        title = "Items",
                        value = "${items.size}",
                        subtitle = "Curated pieces",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Outfits",
                        value = "${outfits.size}",
                        subtitle = "Ready to wear",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Most Worn",
                        value = mostWornItem?.name?.take(12) ?: "—",
                        subtitle = if (mostWornItem != null) "${mostWornItem.wearCount} times" else "Start wearing",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recently Added Row
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Added",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )
                        Text(
                            text = "${items.size} total",
                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }

                    if (items.isEmpty()) {
                        AppleCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tap + to add your clothes",
                                    style = AppleTheme.typography.body.copy(color = colors.textSecondary)
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                        ) {
                            items(items.take(8), key = { it.id }) { item ->
                                RecentlyAddedItemCard(
                                    item = item,
                                    onClick = { onOpenItemDetail(item) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Sparkle Button for AI Stylist (raised above nav pill so zero buttons overlap)
        FloatingActionButton(
            onClick = onNavigateToStylist,
            containerColor = colors.accent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = 104.dp)
                .shadow(12.dp, CircleShape)
                .testTag("ai_stylist_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Stylist",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppleSpacing.Space8))
                Text(
                    text = "AI Stylist",
                    style = AppleTheme.typography.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun TodayOutfitHeroCard(
    outfit: Outfit,
    allItems: List<ClothingItem>,
    onWearThis: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors
    val outfitItems = remember(outfit, allItems) {
        val map = allItems.associateBy { it.id }
        outfit.itemIds.mapNotNull { map[it] }
    }

    AppleGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("today_outfit_card"),
        shape = RoundedCornerShape(24.dp),
        elevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(AppleSpacing.Space24)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Spacer(modifier = Modifier.width(AppleSpacing.Space8))
                    Text(
                        text = "TODAY'S OUTFIT",
                        style = AppleTheme.typography.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.bgSecondary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = outfit.occasion,
                        style = AppleTheme.typography.caption.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            Text(
                text = outfit.name,
                style = AppleTheme.typography.title.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

            // Item Thumbnail Collage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
            ) {
                if (outfitItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(AppleSpacing.InputCornerRadius))
                            .background(colors.bgSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pieces in rotation",
                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                    }
                } else {
                    outfitItems.take(4).forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clip(RoundedCornerShape(AppleSpacing.InputCornerRadius))
                                .border(1.dp, colors.cardBorder, RoundedCornerShape(AppleSpacing.InputCornerRadius))
                                .background(colors.bgSecondary)
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
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

            // Actions: Regenerate (Secondary) and Wear This (Primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
            ) {
                AppleButton(
                    text = "Regenerate",
                    onClick = onRegenerate,
                    isPrimary = false,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "regenerate_outfit_button"
                )

                AppleButton(
                    text = "Wear this",
                    onClick = onWearThis,
                    isPrimary = true,
                    modifier = Modifier.weight(1.3f),
                    testTag = "wear_this_button"
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors

    AppleCard(
        modifier = modifier.height(96.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppleSpacing.Space16),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = AppleTheme.typography.title.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = AppleTheme.typography.caption.copy(
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            )
        }
    }
}

@Composable
fun RecentlyAddedItemCard(
    item: ClothingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppleTheme.colors

    AppleCard(
        modifier = modifier
            .width(130.dp)
            .height(180.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
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

                if (item.favorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = "Favorite",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name,
                    style = AppleTheme.typography.caption.copy(
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.category,
                    style = AppleTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
