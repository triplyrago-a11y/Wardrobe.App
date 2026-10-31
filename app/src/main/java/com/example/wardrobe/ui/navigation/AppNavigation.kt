package com.example.wardrobe.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.AppleTheme
import com.example.ui.theme.WardrobeAITheme
import com.example.wardrobe.auth.ui.AuthFlowScreen
import com.example.wardrobe.auth.viewmodel.AuthViewModel
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.example.wardrobe.ui.components.AppleToast
import com.example.wardrobe.ui.components.FloatingBottomBar
import com.example.wardrobe.ui.components.GlassVideoLoadingOverlay
import com.example.wardrobe.ui.components.ScreenTab
import com.example.wardrobe.ui.screens.home.HomeScreen
import com.example.wardrobe.ui.screens.outfits.OutfitCanvasBuilder
import com.example.wardrobe.ui.screens.outfits.OutfitsScreen
import com.example.wardrobe.ui.screens.planner.PlannerScreen
import com.example.wardrobe.ui.screens.profile.ProfileScreen
import com.example.wardrobe.ui.screens.splash.AnimatedSplashScreen
import com.example.wardrobe.ui.screens.stylist.AiStylistScreen
import com.example.wardrobe.ui.screens.wardrobe.AddItemScreen
import com.example.wardrobe.ui.screens.wardrobe.ItemDetailSheet
import com.example.wardrobe.ui.screens.wardrobe.WardrobeScreen
import com.example.wardrobe.ui.viewmodel.PlannerViewModel
import com.example.wardrobe.ui.viewmodel.SettingsViewModel
import com.example.wardrobe.ui.viewmodel.StylistViewModel
import com.example.wardrobe.ui.viewmodel.WardrobeViewModel

enum class ActiveScreen {
    MAIN,
    ADD_ITEM,
    CANVAS_BUILDER,
    AI_STYLIST
}

@Composable
fun WardrobeAppRoot(
    isAppReady: Boolean = true,
    onSessionCheckDone: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = viewModel()
    val wardrobeViewModel: WardrobeViewModel = viewModel()
    val stylistViewModel: StylistViewModel = viewModel()
    val plannerViewModel: PlannerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDark
    }

    // Collect the persistent first-launch flag from DataStore and live Supabase auth state
    val isAuthIntroCompleted by authViewModel.isAuthIntroCompleted.collectAsStateWithLifecycle(initialValue = null)
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Notify MainActivity once the initial flag is loaded to safely release the system splash
    LaunchedEffect(isAuthIntroCompleted) {
        if (isAuthIntroCompleted != null) {
            onSessionCheckDone()
        }
    }

    WardrobeAITheme(darkTheme = isDarkTheme) {
        val colors = AppleTheme.colors

        // Session flags
        var splashDone by rememberSaveable { mutableStateOf(false) }
        var authCompletedInSession by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(isAuthIntroCompleted, authState) {
            if (isAuthIntroCompleted == false && authState is com.example.wardrobe.auth.model.AuthUiState.Idle) {
                authCompletedInSession = false
            }
        }

        val isSessionActive = authState is com.example.wardrobe.auth.model.AuthUiState.Success
        val showMainApp = (isAuthIntroCompleted == true) || authCompletedInSession || isSessionActive

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgPrimary)
        ) {
            when {
                // Initial check: keep blank matching system splash while DataStore loads
                isAuthIntroCompleted == null -> {
                    // System splash is displayed via setKeepOnScreenCondition
                }

                // Video Splash Screen: 1st video (splash_light.mp4) for Light theme,
                // 2nd video (splash_dark.mp4) for Dark theme
                !splashDone -> {
                    AnimatedSplashScreen(
                        isAppReady = isAppReady,
                        onAnimationComplete = { splashDone = true }
                    )
                }

                // FIRST LAUNCH on device: Login / Sign Up -> Home
                !showMainApp -> {
                    AuthFlowScreen(
                        viewModel = authViewModel,
                        onAuthComplete = {
                            authCompletedInSession = true
                        }
                    )
                }

                // MAIN WARDROBE HOME EXPERIENCE
                else -> {
                    WardrobeMainNavigation(
                        wardrobeViewModel = wardrobeViewModel,
                        stylistViewModel = stylistViewModel,
                        plannerViewModel = plannerViewModel,
                        settingsViewModel = settingsViewModel,
                        authViewModel = authViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun WardrobeMainNavigation(
    wardrobeViewModel: WardrobeViewModel,
    stylistViewModel: StylistViewModel,
    plannerViewModel: PlannerViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(ScreenTab.HOME) }
    var activeScreen by rememberSaveable { mutableStateOf(ActiveScreen.MAIN) }
    var isPageTransitionLoading by rememberSaveable { mutableStateOf(false) }
    var navSwitchCount by rememberSaveable { mutableStateOf(0) }

    fun triggerRareLoading() {
        navSwitchCount++
        if (navSwitchCount >= 5) {
            navSwitchCount = 0
            isPageTransitionLoading = true
        }
    }

    val blurRadius by animateDpAsState(
        targetValue = if (isPageTransitionLoading) 18.dp else 0.dp,
        animationSpec = tween(300),
        label = "screen_blur_radius"
    )

    // Sub-screen parameters
    var editingItem by remember { mutableStateOf<ClothingItem?>(null) }
    var editingOutfit by remember { mutableStateOf<Outfit?>(null) }
    var openCameraOnAdd by rememberSaveable { mutableStateOf(false) }

    val selectedDetailItem by wardrobeViewModel.selectedDetailItem.collectAsStateWithLifecycle()
    val toastMessage by wardrobeViewModel.toastMessage.collectAsStateWithLifecycle()
    val userName by settingsViewModel.userName.collectAsStateWithLifecycle()
    val allItems by wardrobeViewModel.allItems.collectAsStateWithLifecycle()

    val colors = AppleTheme.colors

    // Back button handling
    BackHandler(enabled = activeScreen != ActiveScreen.MAIN) {
        activeScreen = ActiveScreen.MAIN
        editingItem = null
        editingOutfit = null
        openCameraOnAdd = false
    }

    BackHandler(enabled = activeScreen == ActiveScreen.MAIN && currentTab != ScreenTab.HOME) {
        currentTab = ScreenTab.HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        val contentModifier = if (blurRadius > 0.dp) {
            Modifier.fillMaxSize().blur(blurRadius)
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = contentModifier) {
            AnimatedContent(
                targetState = activeScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    ActiveScreen.MAIN -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (currentTab) {
                                    ScreenTab.HOME -> {
                                        HomeScreen(
                                            viewModel = wardrobeViewModel,
                                            userName = userName,
                                            onNavigateToAdd = {
                                                editingItem = null
                                                openCameraOnAdd = false
                                                activeScreen = ActiveScreen.ADD_ITEM
                                                triggerRareLoading()
                                            },
                                            onNavigateToStylist = {
                                                currentTab = ScreenTab.AI
                                                triggerRareLoading()
                                            },
                                            onOpenItemDetail = { item ->
                                                wardrobeViewModel.openDetail(item)
                                            }
                                        )
                                    }
                                    ScreenTab.WARDROBE -> {
                                        WardrobeScreen(
                                            viewModel = wardrobeViewModel,
                                            onNavigateToAdd = {
                                                editingItem = null
                                                openCameraOnAdd = false
                                                activeScreen = ActiveScreen.ADD_ITEM
                                                triggerRareLoading()
                                            },
                                            onNavigateToCameraCapture = {
                                                editingItem = null
                                                openCameraOnAdd = true
                                                activeScreen = ActiveScreen.ADD_ITEM
                                            },
                                            onOpenItemDetail = { item ->
                                                wardrobeViewModel.openDetail(item)
                                            }
                                        )
                                    }
                                    ScreenTab.OUTFITS -> {
                                        OutfitsScreen(
                                            viewModel = wardrobeViewModel,
                                            onNavigateToCanvasBuilder = { outfit ->
                                                editingOutfit = outfit
                                                activeScreen = ActiveScreen.CANVAS_BUILDER
                                                triggerRareLoading()
                                            },
                                            onNavigateToStylist = {
                                                currentTab = ScreenTab.AI
                                                triggerRareLoading()
                                            }
                                        )
                                    }
                                    ScreenTab.AI -> {
                                        AiStylistScreen(
                                            viewModel = stylistViewModel,
                                            onNavigateBack = null,
                                            isBottomBarVisible = true,
                                            onOpenItemDetail = { item ->
                                                wardrobeViewModel.openDetail(item)
                                            }
                                        )
                                    }
                                    ScreenTab.PLANNER -> {
                                        PlannerScreen(
                                            viewModel = plannerViewModel,
                                            onNavigateToOutfits = {
                                                currentTab = ScreenTab.OUTFITS
                                                triggerRareLoading()
                                            }
                                        )
                                    }
                                    ScreenTab.PROFILE -> {
                                        ProfileScreen(
                                            viewModel = settingsViewModel,
                                            onShowToast = { wardrobeViewModel.showToast(it) },
                                            authViewModel = authViewModel
                                        )
                                    }
                                }
                            }

                            // Bottom Navigation Bar with stroke, stretch pill, liquid fill, and sparkle animations
                            FloatingBottomBar(
                                currentTab = currentTab,
                                onTabSelected = { tab ->
                                    if (currentTab != tab) {
                                        currentTab = tab
                                        triggerRareLoading()
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                    ActiveScreen.ADD_ITEM -> {
                        AddItemScreen(
                            viewModel = wardrobeViewModel,
                            existingItem = editingItem,
                            openCameraImmediately = openCameraOnAdd,
                            onNavigateBack = {
                                activeScreen = ActiveScreen.MAIN
                                editingItem = null
                                openCameraOnAdd = false
                            }
                        )
                    }
                    ActiveScreen.CANVAS_BUILDER -> {
                        OutfitCanvasBuilder(
                            allWardrobeItems = allItems,
                            existingOutfit = editingOutfit,
                            onSaveOutfit = { outfit ->
                                wardrobeViewModel.saveOutfit(outfit) {
                                    activeScreen = ActiveScreen.MAIN
                                    editingOutfit = null
                                }
                            },
                            onNavigateBack = {
                                activeScreen = ActiveScreen.MAIN
                                editingOutfit = null
                            }
                        )
                    }
                    ActiveScreen.AI_STYLIST -> {
                        AiStylistScreen(
                            viewModel = stylistViewModel,
                            onNavigateBack = {
                                activeScreen = ActiveScreen.MAIN
                            },
                            isBottomBarVisible = false,
                            onOpenItemDetail = { item ->
                                wardrobeViewModel.openDetail(item)
                            }
                        )
                    }
                }
            }
        }

        // Item Detail Sheet (Modal)
        if (selectedDetailItem != null) {
            ItemDetailSheet(
                item = selectedDetailItem!!,
                onDismiss = { wardrobeViewModel.closeDetail() },
                onEdit = { item ->
                    wardrobeViewModel.closeDetail()
                    editingItem = item
                    activeScreen = ActiveScreen.ADD_ITEM
                },
                onDelete = { item ->
                    wardrobeViewModel.deleteItem(item)
                },
                onToggleFavorite = { item ->
                    wardrobeViewModel.toggleFavorite(item)
                },
                onWearItem = { item ->
                    wardrobeViewModel.wearTodayOutfit(
                        Outfit(name = "Single Piece Wear", itemIds = listOf(item.id))
                    )
                }
            )
        }

        // Top Toast Pill
        AppleToast(
            message = toastMessage ?: "",
            visible = toastMessage != null,
            onDismiss = { wardrobeViewModel.clearToast() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Glass Video Loading Overlay for page transitions
        GlassVideoLoadingOverlay(
            visible = isPageTransitionLoading,
            onDismiss = { isPageTransitionLoading = false }
        )
    }
}
