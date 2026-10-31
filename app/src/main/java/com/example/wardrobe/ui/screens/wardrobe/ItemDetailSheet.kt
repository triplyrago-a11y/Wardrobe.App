package com.example.wardrobe.ui.screens.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: ClothingItem,
    onDismiss: () -> Unit,
    onEdit: (ClothingItem) -> Unit,
    onDelete: (ClothingItem) -> Unit,
    onToggleFavorite: (ClothingItem) -> Unit,
    onWearItem: (ClothingItem) -> Unit
) {
    val colors = AppleTheme.colors
    var showDeleteDialog by remember { mutableStateOf(false) }

    val costPerWear = if (item.wearCount > 0) {
        String.format("₹%.2f", item.price / item.wearCount)
    } else {
        String.format("₹%.2f", item.price)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bgPrimary,
        shape = RoundedCornerShape(topStart = AppleSpacing.SheetCornerRadius, topEnd = AppleSpacing.SheetCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header with Close, Favorite, Edit, Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleFavorite(item) }) {
                        Icon(
                            imageVector = if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.favorite) Color(0xFFFF2D55) else colors.textSecondary
                        )
                    }
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.accent)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.destructive)
                    }
                }
            }

            // Hero Image
            val isTransparentCutout = item.imageUri.endsWith(".png", ignoreCase = true)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpacing.ScreenPadding)
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(AppleSpacing.CardCornerRadius))
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
                        .padding(if (isTransparentCutout) 14.dp else 0.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

            // Item Name & Category Badge
            Column(modifier = Modifier.padding(horizontal = AppleSpacing.ScreenPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = AppleTheme.typography.title.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.bgSecondary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = AppleTheme.typography.caption.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent
                            )
                        )
                    }
                }

                if (item.brand.isNotBlank()) {
                    Text(
                        text = item.brand,
                        style = AppleTheme.typography.body.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                // Quick Stats: Wears & Cost Per Wear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    AppleCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Times Worn",
                                style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${item.wearCount}",
                                style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                            )
                        }
                    }

                    AppleCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Cost / Wear",
                                style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = costPerWear,
                                style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                            )
                        }
                    }

                    AppleCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Price",
                                style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${item.price.toInt()}",
                                style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                // Detail Specification rows
                DetailInfoRow(label = "Material", value = item.material)
                DetailInfoRow(label = "Pattern", value = item.pattern)
                DetailInfoRow(label = "Size", value = item.size)
                DetailInfoRow(label = "Colors", value = item.colors.joinToString(", "))
                DetailInfoRow(label = "Seasons", value = item.seasons.joinToString(", "))
                DetailInfoRow(label = "Occasions", value = item.occasions.joinToString(", "))

                if (item.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(AppleSpacing.Space16))
                    Text(
                        text = "Notes & Styling",
                        style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.notes,
                        style = AppleTheme.typography.body.copy(color = colors.textPrimary, fontSize = 15.sp)
                    )
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                AppleButton(
                    text = "Log Wear Today",
                    onClick = {
                        onWearItem(item)
                        onDismiss()
                    },
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showDeleteDialog) {
        AppleConfirmationDialog(
            title = "Delete Item",
            message = "Are you sure you want to remove '${item.name}' from your wardrobe? This action cannot be undone.",
            confirmText = "Delete",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                onDelete(item)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    val colors = AppleTheme.colors
    if (value.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppleTheme.typography.body.copy(color = colors.textSecondary, fontSize = 15.sp)
        )
        Text(
            text = value,
            style = AppleTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        )
    }
}
