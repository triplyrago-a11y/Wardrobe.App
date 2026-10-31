package com.example.wardrobe.ui.screens.wardrobe

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleChip
import com.example.wardrobe.ui.components.AppleInputField
import com.example.wardrobe.ui.components.GarmentOutlineScannerView
import com.example.wardrobe.ui.viewmodel.WardrobeViewModel
import com.example.wardrobe.vision.GarmentSegmentationEngine
import com.example.wardrobe.vision.GarmentSegmentationResult
import com.example.wardrobe.vision.UserSelectionMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddItemScreen(
    viewModel: WardrobeViewModel,
    existingItem: ClothingItem? = null,
    openCameraImmediately: Boolean = false,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppleTheme.colors

    var name by remember { mutableStateOf(existingItem?.name ?: "") }
    var category by remember { mutableStateOf(existingItem?.category ?: "Tops") }
    var colorText by remember { mutableStateOf(existingItem?.colors?.joinToString(", ") ?: "Navy") }
    var brand by remember { mutableStateOf(existingItem?.brand ?: "") }
    var size by remember { mutableStateOf(existingItem?.size ?: "M") }
    var material by remember { mutableStateOf(existingItem?.material ?: "Cotton") }
    var pattern by remember { mutableStateOf(existingItem?.pattern ?: "Solid") }
    var selectedSeasons by remember { mutableStateOf(existingItem?.seasons ?: listOf("All-Season")) }
    var selectedOccasions by remember { mutableStateOf(existingItem?.occasions ?: listOf("Casual")) }
    var priceText by remember { mutableStateOf(if (existingItem != null && existingItem.price > 0) existingItem.price.toInt().toString() else "") }
    var notes by remember { mutableStateOf(existingItem?.notes ?: "") }
    var imageUri by remember { mutableStateOf(existingItem?.imageUri ?: "") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showCameraXCapture by remember { mutableStateOf(openCameraImmediately) }
    var isQuickSavingFromCamera by remember { mutableStateOf(false) }

    // Manual Garment Selection & Contour Scanner state for Gallery / Camera imports
    val coroutineScope = rememberCoroutineScope()
    var activeSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isManualSelectionMode by remember { mutableStateOf(false) }
    var userMarkup by remember { mutableStateOf(UserSelectionMarkup()) }
    var segmentationResult by remember { mutableStateOf<GarmentSegmentationResult?>(null) }
    var isSegmentingGarment by remember { mutableStateOf(false) }
    var selectedCandidateIndex by remember { mutableIntStateOf(0) }

    val isAnalyzing by viewModel.isAnalyzingImage.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()

    // Trigger manual garment segmentation + transparent PNG export ONLY after user taps Extract Clothing
    fun triggerManualSegmentation(bitmap: Bitmap, markup: UserSelectionMarkup) {
        activeSourceBitmap = bitmap
        isManualSelectionMode = false
        isSegmentingGarment = true
        segmentationResult = null
        selectedCandidateIndex = 0
        coroutineScope.launch {
            val res = GarmentSegmentationEngine.segmentGarmentWithUserMarkup(bitmap, markup)
            segmentationResult = res
            if (res is GarmentSegmentationResult.Success) {
                val primary = res.primaryCandidate
                val pngFile = withContext(Dispatchers.IO) {
                    GarmentSegmentationEngine.exportTransparentPng(context, primary.cutoutBitmap)
                }
                imageUri = Uri.fromFile(pngFile).toString()
                viewModel.analyzeImageWithGemini(primary.cutoutBitmap)
            }
            isSegmentingGarment = false
        }
    }

    // Re-export PNG if user selects a different detected garment candidate
    LaunchedEffect(selectedCandidateIndex, segmentationResult) {
        val success = segmentationResult as? GarmentSegmentationResult.Success ?: return@LaunchedEffect
        val candidate = success.candidates.getOrNull(selectedCandidateIndex) ?: return@LaunchedEffect
        val pngFile = withContext(Dispatchers.IO) {
            GarmentSegmentationEngine.exportTransparentPng(context, candidate.cutoutBitmap)
        }
        imageUri = Uri.fromFile(pngFile).toString()
    }

    // When Gemini vision analysis completes, auto-fill fields!
    LaunchedEffect(analysisResult) {
        analysisResult?.let { analysis ->
            if (name.isBlank() || name == "New Garment") name = analysis.suggestedName
            if (analysis.category.isNotBlank()) category = analysis.category
            if (analysis.colors.isNotEmpty()) colorText = analysis.colors.joinToString(", ")
            if (analysis.material.isNotBlank()) material = analysis.material
            if (analysis.pattern.isNotBlank()) pattern = analysis.pattern
            if (analysis.brand.isNotBlank()) brand = analysis.brand
            if (analysis.seasons.isNotEmpty()) selectedSeasons = analysis.seasons
            if (analysis.occasions.isNotEmpty()) selectedOccasions = analysis.occasions
            if (analysis.styleNotes.isNotBlank() && notes.isBlank()) notes = analysis.styleNotes
        }
    }

    // Photo picker (Zero-permission Android Photo Picker) -> Interactive Manual Selection Screen
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val orientedBitmap = withContext(Dispatchers.IO) {
                    GarmentSegmentationEngine.loadOrientedBitmap(context, it)
                } ?: try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, it)
                        ) { decoder, _, _ -> decoder.isMutableRequired = true }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                    }
                } catch (_: Exception) {
                    null
                }

                if (orientedBitmap != null) {
                    // Do NOT automatically segment! Let the user mark the desired garment first.
                    activeSourceBitmap = orientedBitmap
                    userMarkup = UserSelectionMarkup()
                    segmentationResult = null
                    isSegmentingGarment = false
                    isManualSelectionMode = true
                    imageUri = it.toString()
                } else {
                    imageUri = it.toString()
                }
            }
        }
    }

    if (showCameraXCapture) {
        CameraCaptureScreen(
            isSavingQuickItem = isQuickSavingFromCamera,
            onPhotoConfirmedForForm = { capturedUri, capturedBitmap ->
                imageUri = capturedUri.toString()
                showCameraXCapture = false
                val isAlreadyExtractedPng = capturedUri.toString().endsWith(".png", ignoreCase = true)
                if (isAlreadyExtractedPng) {
                    // User already manually selected and extracted the garment in CameraCaptureScreen
                    activeSourceBitmap = null
                    segmentationResult = null
                    isManualSelectionMode = false
                    if (capturedBitmap != null) {
                        viewModel.analyzeImageWithGemini(capturedBitmap)
                    }
                } else if (capturedBitmap != null) {
                    activeSourceBitmap = capturedBitmap
                    userMarkup = UserSelectionMarkup()
                    segmentationResult = null
                    isManualSelectionMode = true
                }
            },
            onQuickSaveToWardrobe = { capturedUri, capturedBitmap ->
                isQuickSavingFromCamera = true
                viewModel.quickSaveCapturedItem(
                    imageUri = capturedUri.toString(),
                    bitmap = capturedBitmap
                ) {
                    isQuickSavingFromCamera = false
                    showCameraXCapture = false
                    onNavigateBack()
                }
            },
            onCancel = {
                showCameraXCapture = false
                if (openCameraImmediately && imageUri.isBlank() && existingItem == null) {
                    onNavigateBack()
                }
            }
        )
        return
    }

    fun submitGarmentToWardrobe() {
        if (isSaving || isSegmentingGarment) return
        val resolvedName = name.trim().ifBlank {
            analysisResult?.suggestedName?.takeIf { it.isNotBlank() } ?: "Isolated $category Piece"
        }
        name = resolvedName
        nameError = null
        isSaving = true

        val finalPrice = priceText.toDoubleOrNull() ?: 0.0
        val colorsList = colorText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val itemToSave = ClothingItem(
            id = existingItem?.id ?: 0,
            name = resolvedName,
            imageUri = imageUri.ifBlank { "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?auto=format&fit=crop&w=600&q=80" },
            category = category,
            colors = colorsList.ifEmpty { listOf("Neutral") },
            pattern = pattern,
            material = material,
            brand = brand.trim(),
            size = size.trim(),
            seasons = selectedSeasons,
            occasions = selectedOccasions,
            price = finalPrice,
            notes = notes.trim(),
            favorite = existingItem?.favorite ?: false,
            wearCount = existingItem?.wearCount ?: 0,
            lastWorn = existingItem?.lastWorn,
            createdAt = existingItem?.createdAt ?: System.currentTimeMillis()
        )

        viewModel.saveItem(itemToSave, isNew = (existingItem == null)) {
            isSaving = false
            onNavigateBack()
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
            // Top Navigation Bar
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
                    text = if (existingItem == null) "New Piece" else "Edit Piece",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                )

                // Save Header Action
                AppleButton(
                    text = "Save",
                    onClick = { submitGarmentToWardrobe() },
                    isPrimary = true,
                    isLoading = isSaving,
                    enabled = !isSaving && !isSegmentingGarment && !isManualSelectionMode,
                    modifier = Modifier.height(36.dp),
                    testTag = "save_item_button"
                )
            }

            // Scrollable Form Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = !isManualSelectionMode)
                    .padding(horizontal = AppleSpacing.ScreenPadding, vertical = 8.dp)
                    .padding(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
            ) {
                // Photo & Interactive Manual Garment Outline Scanner Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isManualSelectionMode) 0.82f else 1.12f)
                        .clip(RoundedCornerShape(AppleSpacing.CardCornerRadius))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(AppleSpacing.CardCornerRadius))
                        .background(colors.bgSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeSourceBitmap != null || segmentationResult != null) {
                        GarmentOutlineScannerView(
                            sourceBitmap = activeSourceBitmap,
                            segmentationResult = segmentationResult,
                            isProcessing = isSegmentingGarment,
                            selectedCandidateIndex = selectedCandidateIndex,
                            onSelectCandidate = { idx -> selectedCandidateIndex = idx },
                            onIsolationComplete = { _ -> },
                            onRetrySegmentation = {
                                val bmp = activeSourceBitmap
                                if (bmp != null && userMarkup.hasPositiveSelection) {
                                    triggerManualSegmentation(bmp, userMarkup)
                                } else {
                                    isManualSelectionMode = true
                                }
                            },
                            userMarkup = userMarkup,
                            onUserMarkupChange = { userMarkup = it },
                            isManualSelectionMode = isManualSelectionMode,
                            onExtractClothing = { markup ->
                                userMarkup = markup
                                activeSourceBitmap?.let { bmp ->
                                    triggerManualSegmentation(bmp, markup)
                                }
                            },
                            onRequestEditSelection = {
                                isManualSelectionMode = true
                                segmentationResult = null
                            },
                            darkBackdrop = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (imageUri.isNotBlank()) {
                        val isCutoutPng = imageUri.endsWith(".png", ignoreCase = true)
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Garment Preview",
                            contentScale = if (isCutoutPng) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (isCutoutPng) 12.dp else 0.dp)
                        )
                    }

                    // Floating action pills to capture with CameraX or upload from Gallery (hidden while drawing selection)
                    if (!isManualSelectionMode) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space12)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .testTag("add_item_camera_button")
                                    .clickable {
                                        showCameraXCapture = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera", style = AppleTheme.typography.caption.copy(color = Color.White, fontWeight = FontWeight.Medium))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .testTag("add_item_gallery_button")
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Gallery", style = AppleTheme.typography.caption.copy(color = Color.White, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }

                    // Subtle Gemini metadata badge when analyzing in parallel
                    if (isAnalyzing && activeSourceBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Analyzing Garment...",
                                        style = AppleTheme.typography.caption.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    }
                }

                // Garment Name
                AppleInputField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = "Piece Name *",
                    placeholder = "e.g. Crisp Poplin Button-Down",
                    errorMessage = nameError,
                    testTag = "input_piece_name"
                )

                // Category Chips
                Column {
                    Text(
                        text = "Category",
                        style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary),
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
                    ) {
                        listOf("Tops", "Bottoms", "Dresses", "Outerwear", "Shoes", "Accessories").forEach { cat ->
                            AppleChip(
                                text = cat,
                                selected = category.equals(cat, ignoreCase = true),
                                onClick = { category = cat },
                                testTag = "add_cat_$cat"
                            )
                        }
                    }
                }

                // Brand & Size Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    AppleInputField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = "Brand",
                        placeholder = "e.g. Theory, COS",
                        modifier = Modifier.weight(1.3f)
                    )
                    AppleInputField(
                        value = size,
                        onValueChange = { size = it },
                        label = "Size",
                        placeholder = "e.g. M, 32",
                        modifier = Modifier.weight(0.7f)
                    )
                }

                // Material & Pattern Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    AppleInputField(
                        value = material,
                        onValueChange = { material = it },
                        label = "Fabric / Material",
                        placeholder = "e.g. Merino Wool",
                        modifier = Modifier.weight(1f)
                    )
                    AppleInputField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = "Pattern",
                        placeholder = "e.g. Solid, Striped",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Colors & Price Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                ) {
                    AppleInputField(
                        value = colorText,
                        onValueChange = { colorText = it },
                        label = "Colors",
                        placeholder = "e.g. Charcoal, Ivory",
                        modifier = Modifier.weight(1.2f)
                    )
                    AppleInputField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = "Price (₹)",
                        placeholder = "e.g. 2499",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                // Seasons Selection
                Column {
                    Text(
                        text = "Seasons",
                        style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary),
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
                    ) {
                        listOf("Spring", "Summer", "Fall", "Winter", "All-Season").forEach { season ->
                            val isSelected = selectedSeasons.contains(season)
                            AppleChip(
                                text = season,
                                selected = isSelected,
                                onClick = {
                                    val current = selectedSeasons.toMutableList()
                                    if (isSelected) current.remove(season) else current.add(season)
                                    selectedSeasons = current
                                }
                            )
                        }
                    }
                }

                // Occasions Selection
                Column {
                    Text(
                        text = "Occasions",
                        style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textSecondary),
                        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space8)
                    ) {
                        listOf("Casual", "Work", "Formal", "Party", "Sport", "Loungewear").forEach { occ ->
                            val isSelected = selectedOccasions.contains(occ)
                            AppleChip(
                                text = occ,
                                selected = isSelected,
                                onClick = {
                                    val current = selectedOccasions.toMutableList()
                                    if (isSelected) current.remove(occ) else current.add(occ)
                                    selectedOccasions = current
                                }
                            )
                        }
                    }
                }

                // Notes
                AppleInputField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Styling Notes & Fit",
                    placeholder = "e.g. Boxy drape, looks best tucked into high-waist pants",
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Bottom Action Button: Add to Wardrobe
                AppleButton(
                    text = if (existingItem == null) "Add to Wardrobe" else "Update Piece",
                    onClick = { submitGarmentToWardrobe() },
                    isPrimary = true,
                    isLoading = isSaving,
                    enabled = !isSaving && !isSegmentingGarment && !isManualSelectionMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    testTag = "add_to_wardrobe_submit_button"
                )
            }
        }
    }
}
