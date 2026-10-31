package com.example.wardrobe.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleCard
import com.example.wardrobe.ui.components.AppleChip
import com.example.wardrobe.ui.components.AppleConfirmationDialog
import com.example.wardrobe.ui.components.AppleInputField
import com.example.wardrobe.ui.viewmodel.SettingsViewModel
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import com.example.wardrobe.auth.model.AuthState
import com.example.wardrobe.auth.ui.ApplePasswordField
import com.example.wardrobe.auth.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SettingsViewModel,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null
) {
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val stylePref by viewModel.stylePreference.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val notifsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val exportJson by viewModel.exportJsonString.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatusMessage.collectAsStateWithLifecycle()

    val colors = AppleTheme.colors
    val context = LocalContext.current

    var showClearDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showReauthSheet by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }
    var isUpdatingPassword by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }
    var isEditingProfile by remember { mutableStateOf(false) }
    var editName by remember(userName) { mutableStateOf(userName) }

    val cloudProfile by authViewModel?.currentProfile?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val passwordRecoveryPending by authViewModel?.passwordRecoveryPending?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        authViewModel?.refreshUserProfile()
    }

    androidx.compose.runtime.LaunchedEffect(passwordRecoveryPending) {
        if (passwordRecoveryPending) {
            showChangePasswordSheet = true
        }
    }

    val insights = remember(items) { viewModel.calculateInsights(items) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = AppleSpacing.MaxContentWidth)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = AppleSpacing.ScreenPadding,
                end = AppleSpacing.ScreenPadding,
                top = 24.dp,
                bottom = 164.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppleSpacing.GroupSpacing)
        ) {
            // Header: Profile & Avatar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(colors.bgSecondary)
                            .border(1.5.dp, colors.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = colors.accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cloudProfile?.fullName?.ifBlank { null } ?: userName,
                            style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stylePref,
                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                        )
                        if (!cloudProfile?.email.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cloudProfile?.email ?: "",
                                style = AppleTheme.typography.caption.copy(color = colors.accent, fontSize = 12.sp)
                            )
                        }
                    }

                    Text(
                        text = if (isEditingProfile) "Save" else "Edit",
                        style = AppleTheme.typography.body.copy(color = colors.accent, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier
                            .testTag("profile_edit_save_button")
                            .clickable {
                                if (isEditingProfile) {
                                    viewModel.updateName(editName) { _, msg ->
                                        onShowToast(msg)
                                    }
                                    authViewModel?.updateUserProfile(
                                        fullName = editName,
                                        stylePreference = stylePref
                                    )
                                    isEditingProfile = false
                                } else {
                                    editName = cloudProfile?.fullName?.ifBlank { null } ?: userName
                                    isEditingProfile = true
                                }
                            }
                    )
                }
            }

            if (isEditingProfile) {
                item {
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            AppleInputField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = "Full Name (public.profiles)",
                                placeholder = "Enter your full name",
                                testTag = "profile_edit_name_input"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Preferred Aesthetic",
                                style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Modern Minimalist", "Old Money", "Monochrome", "Casual Parisian", "Streetwear").forEach { aesthetic ->
                                    AppleChip(
                                        text = aesthetic,
                                        selected = stylePref == aesthetic,
                                        onClick = {
                                            viewModel.updateStylePreference(aesthetic) { _, msg ->
                                                onShowToast(msg)
                                            }
                                            authViewModel?.updateUserProfile(stylePreference = aesthetic)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Wardrobe Insights Section
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wardrobe Analytics & Cost Per Wear",
                            style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppleSpacing.Space16)
                    ) {
                        AppleCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Total Value", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${insights.totalValue.toInt()}",
                                    style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                                )
                            }
                        }

                        AppleCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Avg. Cost / Wear", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format("₹%.2f", insights.averageCostPerWear),
                                    style = AppleTheme.typography.title.copy(fontSize = 22.sp, color = colors.textPrimary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                    // Category Breakdown Chart (Custom Compose Proportion Bar)
                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Category Distribution",
                                style = AppleTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Segmented bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(CircleShape)
                                    .background(colors.bgPrimary)
                            ) {
                                val palette = listOf(
                                    colors.accent,
                                    Color(0xFF5856D6),
                                    Color(0xFFFF2D55),
                                    Color(0xFFFF9500),
                                    Color(0xFF34C759),
                                    Color(0xFFAF52DE)
                                )

                                insights.categoryBreakdown.forEachIndexed { index, cat ->
                                    if (cat.percentage > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(cat.percentage)
                                                .fillMaxSize()
                                                .background(palette[index % palette.size])
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Legend list
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val palette = listOf(
                                    colors.accent,
                                    Color(0xFF5856D6),
                                    Color(0xFFFF2D55),
                                    Color(0xFFFF9500),
                                    Color(0xFF34C759),
                                    Color(0xFFAF52DE)
                                )
                                insights.categoryBreakdown.forEachIndexed { index, cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(palette[index % palette.size])
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat.category,
                                                style = AppleTheme.typography.caption.copy(color = colors.textPrimary)
                                            )
                                        }
                                        Text(
                                            text = "${cat.count} (${(cat.percentage * 100).toInt()}%)",
                                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // App Appearance & Settings
            item {
                Column {
                    Text(
                        text = "Preferences",
                        style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Theme Selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Brightness4, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Appearance", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                        AppleChip(
                                            text = mode.lowercase().replaceFirstChar { it.uppercase() },
                                            selected = themeMode == mode,
                                            onClick = { viewModel.setThemeMode(mode) }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Notifications Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Evening Outfit Notification", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                    Text("Get tomorrow's look ready at 8 PM", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                }
                                Switch(
                                    checked = notifsEnabled,
                                    onCheckedChange = { viewModel.setNotifications(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent)
                                )
                            }
                        }
                    }
                }
            }

            // Data Management: Export, Import, Reset, Reload Demo
            item {
                Column {
                    Text(
                        text = "Data Management",
                        style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Export Wardrobe JSON
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.exportData()
                                        onShowToast("Wardrobe JSON exported!")
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Export Wardrobe (JSON)", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }

                            // If JSON exported, show copy action
                            if (exportJson != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.bgPrimary)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Wardrobe JSON", exportJson)
                                            clipboard.setPrimaryClip(clip)
                                            onShowToast("JSON copied to clipboard!")
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Tap to Copy Exported JSON (${exportJson!!.length} chars)",
                                        style = AppleTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Medium)
                                    )
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Import Wardrobe JSON
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showImportSheet = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Import Wardrobe (JSON)", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Reload 12 Demo Items
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.reloadDemoData()
                                        onShowToast("12 Demo items reloaded!")
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Reload 12 Demo Items", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Clear All Data
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Clear All Data", style = AppleTheme.typography.body.copy(color = colors.destructive, fontWeight = FontWeight.Medium))
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Reset First-Launch / Auth Flag (Testing & Verification)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.resetAuthIntro()
                                        onShowToast("First-launch flag reset! Restart app to view Auth flow.")
                                    }
                                    .testTag("reset_auth_flag_row"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Reset First-Launch / Auth Flag", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                        Text("Forces the Auth Splash & Login to run on next launch", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Account & Security Section
            item {
                Column {
                    Text(
                        text = "Account & Security",
                        style = AppleTheme.typography.title.copy(fontSize = 18.sp, color = colors.textPrimary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val authState by authViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
                    val authenticatedUser = (authState as? com.example.wardrobe.auth.model.AuthUiState.Success)?.user
                    val activeEmail = cloudProfile?.email ?: authenticatedUser?.email
                    val isSupabaseAuthenticated = authenticatedUser != null && !authenticatedUser.isGuest && !activeEmail.isNullOrBlank()

                    AppleCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // User email / Supabase RLS status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = activeEmail ?: "Guest / Local Session",
                                            style = AppleTheme.typography.body.copy(color = colors.textPrimary)
                                        )
                                        Text(
                                            text = if (isSupabaseAuthenticated) {
                                                "Supabase Verified • public.profiles RLS Active"
                                            } else {
                                                "Active Local Session"
                                            },
                                            style = AppleTheme.typography.caption.copy(
                                                color = if (isSupabaseAuthenticated) colors.success else colors.textSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Sync Profile from Supabase (public.profiles)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        authViewModel?.refreshUserProfile { _, msg ->
                                            onShowToast(msg)
                                        }
                                    }
                                    .testTag("profile_sync_supabase_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Sync Profile with Supabase", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                        Text("Fetches latest full_name & profile from public.profiles", style = AppleTheme.typography.caption.copy(color = colors.textSecondary))
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Change / Reset Password
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showChangePasswordSheet = true }
                                    .testTag("profile_change_password_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Change Password", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Sign Out
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSignOutDialog = true }
                                    .testTag("profile_sign_out_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Sign Out", style = AppleTheme.typography.body.copy(color = colors.textPrimary))
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder))

                            // Delete Account
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeleteAccountDialog = true }
                                    .testTag("profile_delete_account_button"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NoAccounts, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Delete Account & Clear All Data", style = AppleTheme.typography.body.copy(color = colors.destructive, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }
                }
            }

            // About & Official Logo Lockup
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.wardrobe.ui.components.WardrobeLogo(
                        mode = com.example.wardrobe.ui.components.LogoMode.Lockup,
                        size = 38.dp,
                        fontSize = 24f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version 1.0.0 (Build 2026.10)",
                        style = AppleTheme.typography.caption.copy(color = colors.textSecondary),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                viewModel.resetAuthIntro()
                                onShowToast("First-launch flag reset! Restart app to view Auth flow.")
                            }
                            .padding(4.dp)
                            .testTag("version_label_debug_reset")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Crafted with Jetpack Compose & Gemini AI",
                        style = AppleTheme.typography.caption.copy(color = colors.textSecondary, fontSize = 11.sp)
                    )
                }
            }
        }
    }

    // Change Password Sheet
    if (showChangePasswordSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showChangePasswordSheet = false
                newPasswordInput = ""
                confirmNewPasswordInput = ""
                com.example.wardrobe.data.remote.SupabaseClientManager.clearPasswordRecoveryFlag()
            },
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
                    text = "Update Password",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter a new password (minimum 8 characters) for your Wardrobe AI Supabase account.",
                    style = AppleTheme.typography.body.copy(fontSize = 14.sp, color = colors.textSecondary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                ApplePasswordField(
                    value = newPasswordInput,
                    onValueChange = { newPasswordInput = it },
                    label = "New Password",
                    placeholder = "Min. 8 characters",
                    testTag = "change_password_new_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                ApplePasswordField(
                    value = confirmNewPasswordInput,
                    onValueChange = { confirmNewPasswordInput = it },
                    label = "Confirm New Password",
                    placeholder = "Re-enter new password",
                    testTag = "change_password_confirm_input"
                )

                Spacer(modifier = Modifier.height(24.dp))

                AppleButton(
                    text = if (isUpdatingPassword) "Updating..." else "Save New Password",
                    onClick = {
                        if (newPasswordInput != confirmNewPasswordInput) {
                            onShowToast("Passwords do not match.")
                            return@AppleButton
                        }
                        isUpdatingPassword = true
                        authViewModel?.updatePassword(newPasswordInput) { success, msg ->
                            isUpdatingPassword = false
                            onShowToast(msg)
                            if (success) {
                                showChangePasswordSheet = false
                                newPasswordInput = ""
                                confirmNewPasswordInput = ""
                            }
                        }
                    },
                    isPrimary = true,
                    isLoading = isUpdatingPassword,
                    enabled = newPasswordInput.length >= 8 && confirmNewPasswordInput.isNotBlank() && !isUpdatingPassword,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "confirm_change_password_button"
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Import JSON Sheet
    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
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
                    text = "Import Wardrobe JSON",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(12.dp))

                AppleInputField(
                    value = importInputText,
                    onValueChange = { importInputText = it },
                    placeholder = "Paste valid JSON array of clothing items here...",
                    singleLine = false,
                    modifier = Modifier.height(140.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                AppleButton(
                    text = "Confirm Import",
                    onClick = {
                        viewModel.importData(importInputText)
                        showImportSheet = false
                        onShowToast("Wardrobe imported!")
                    },
                    isPrimary = true,
                    enabled = importInputText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AppleConfirmationDialog(
            title = "Clear All Wardrobe Data?",
            message = "This will permanently remove all your clothing items, saved outfits, and scheduled planner entries.",
            confirmText = "Clear Everything",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                viewModel.clearAllData()
                showClearDialog = false
                onShowToast("All wardrobe data cleared.")
            },
            onDismiss = { showClearDialog = false }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AppleConfirmationDialog(
            title = "Sign Out?",
            message = "Are you sure you want to sign out of your Wardrobe AI account?",
            confirmText = "Sign Out",
            dismissText = "Cancel",
            isDestructive = false,
            onConfirm = {
                showSignOutDialog = false
                authViewModel?.signOut()
                onShowToast("Signed out successfully.")
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        AppleConfirmationDialog(
            title = "Delete Account?",
            message = "This will permanently delete your account and remove all wardrobe items, outfits, and styling data from this device. This cannot be undone.",
            confirmText = "Continue to Delete",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showDeleteAccountDialog = false
                showReauthSheet = true
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }

    // Re-auth password dialog before deleting account
    if (showReauthSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showReauthSheet = false
                deletePassword = ""
            },
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
                    text = "Confirm Account Deletion",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.destructive)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "For security, please enter your password to confirm and permanently wipe your account and all wardrobe data.",
                    style = AppleTheme.typography.body.copy(fontSize = 14.sp, color = colors.textSecondary)
                )

                Spacer(modifier = Modifier.height(16.dp))

                ApplePasswordField(
                    value = deletePassword,
                    onValueChange = { deletePassword = it },
                    label = "Account Password",
                    placeholder = "Enter your password",
                    testTag = "delete_account_password_input"
                )

                Spacer(modifier = Modifier.height(24.dp))

                AppleButton(
                    text = if (isDeletingAccount) "Deleting..." else "Permanently Delete Account",
                    onClick = {
                        isDeletingAccount = true
                        authViewModel?.deleteAccount(deletePassword) { success, error ->
                            isDeletingAccount = false
                            if (success) {
                                showReauthSheet = false
                                deletePassword = ""
                                onShowToast("Account and data permanently deleted.")
                            } else {
                                onShowToast(error ?: "Failed to delete account. Please verify password.")
                            }
                        }
                    },
                    isPrimary = true,
                    isLoading = isDeletingAccount,
                    enabled = deletePassword.isNotBlank() && !isDeletingAccount,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "confirm_delete_account_button"
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
