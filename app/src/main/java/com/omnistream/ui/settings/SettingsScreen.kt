package com.omnistream.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.omnistream.data.anilist.AniListAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Settings screen with Saikou-inspired clean design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val authManager = viewModel.authManager
    val isAniListConnected = authManager.isLoggedIn()
    val anilistUsername = authManager.getUsername()
    val anilistAvatar = authManager.getAvatar()

    val colorScheme by viewModel.colorScheme.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    var showDnsDialog by remember { mutableStateOf(false) }
    var showAccentColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Settings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // AniList avatar
                    if (isAniListConnected && anilistAvatar != null) {
                        AsyncImage(
                            model = anilistAvatar,
                            contentDescription = "AniList Avatar",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "S",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // Common section
            item { SectionTitle("Common") }

            // Theme - Multi-choice with icon buttons
            item {
                SettingRow(
                    title = "Theme",
                    leftIcon = null
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconToggle(
                            icon = Icons.Default.Settings,
                            selected = darkMode == "system",
                            onClick = { viewModel.setDarkMode("system") }
                        )
                        IconToggle(
                            icon = Icons.Default.DarkMode,
                            selected = darkMode == "dark",
                            onClick = { viewModel.setDarkMode("dark") }
                        )
                        IconToggle(
                            icon = Icons.Default.LightMode,
                            selected = darkMode == "light",
                            onClick = { viewModel.setDarkMode("light") }
                        )
                    }
                }
            }

            // Default Start Up Tab
            item {
                SettingRow(
                    title = "Default Start Up Tab",
                    leftIcon = null
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconToggle(
                            icon = Icons.Default.Movie,
                            selected = true,
                            onClick = {}
                        )
                        IconToggle(
                            icon = Icons.Default.Home,
                            selected = false,
                            onClick = {}
                        )
                        IconToggle(
                            icon = Icons.Default.Book,
                            selected = false,
                            onClick = {}
                        )
                    }
                }
            }

            // UI Settings - Navigation item
            item {
                SettingNavigationRow(
                    icon = Icons.Default.Palette,
                    title = "UI Settings",
                    onClick = {
                        // Navigate to sub-screen or show dialog
                    }
                )
            }

            // Selected DNS - Dropdown style
            item {
                SettingRow(
                    title = "Selected DNS",
                    subtitle = "Change if your ISP blocks any Source",
                    leftIcon = Icons.Default.Dns
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1a1a1a),
                        modifier = Modifier
                            .width(150.dp)
                            .clickable { showDnsDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CLOUDFLARE",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Toggles
            item {
                SettingToggleRow(
                    icon = Icons.Default.Download,
                    title = "Download in SD card",
                    checked = false,
                    onCheckedChange = {}
                )
            }

            item {
                SettingToggleRow(
                    icon = Icons.Default.Update,
                    title = "Only show My Shows in Recently Updated",
                    checked = false,
                    onCheckedChange = {}
                )
            }

            item {
                SettingToggleRow(
                    icon = Icons.Default.VideoLibrary,
                    title = "Show Youtube link",
                    checked = false,
                    onCheckedChange = {}
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Account section
            item { SectionTitle("Account") }

            item {
                if (isAniListConnected && anilistUsername != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1a1a1a), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // AniList Avatar
                            if (anilistAvatar != null) {
                                Surface(
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    AsyncImage(
                                        model = anilistAvatar,
                                        contentDescription = "AniList Avatar",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            Column {
                                Text(
                                    "AniList",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "Connected as $anilistUsername",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        TextButton(onClick = { authManager.logout() }) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    SettingNavigationRow(
                        icon = Icons.Default.Link,
                        title = "Connect AniList",
                        subtitle = "Sync anime & manga progress",
                        onClick = { navController.navigate("anilist_login") }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Appearance section
            item { SectionTitle("Appearance") }

            item {
                SettingNavigationRow(
                    icon = Icons.Default.ColorLens,
                    title = "Accent Color",
                    subtitle = colorScheme.replaceFirstChar { it.uppercase() },
                    onClick = { showAccentColorDialog = true }
                )
            }

            item {
                SettingToggleRow(
                    icon = Icons.Default.Contrast,
                    title = "AMOLED Mode",
                    checked = true,
                    onCheckedChange = {}
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Player section
            item { SectionTitle("Player") }

            item {
                SettingNavigationRow(
                    icon = Icons.Default.HighQuality,
                    title = "Default Quality",
                    subtitle = "Auto (1080p preferred)",
                    onClick = {}
                )
            }

            item {
                SettingToggleRow(
                    icon = Icons.Default.SkipNext,
                    title = "Auto Skip Intro",
                    checked = false,
                    onCheckedChange = {}
                )
            }

            item {
                SettingToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Auto Play Next",
                    checked = true,
                    onCheckedChange = {}
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // Downloads section
            item { SectionTitle("Downloads") }

            item {
                SettingToggleRow(
                    icon = Icons.Default.Wifi,
                    title = "Download over WiFi Only",
                    checked = true,
                    onCheckedChange = {}
                )
            }

            item {
                SettingNavigationRow(
                    icon = Icons.Default.Storage,
                    title = "Manage Downloads",
                    onClick = { navController.navigate("downloads") }
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // About section
            item { SectionTitle("About") }

            item {
                SettingNavigationRow(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }

            item {
                SettingNavigationRow(
                    icon = Icons.Default.Update,
                    title = "Check for Updates",
                    onClick = {}
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Logout
            item {
                SettingNavigationRow(
                    icon = Icons.Default.ExitToApp,
                    title = "Logout",
                    onClick = {
                        viewModel.logout(onLoggedOut = onLogout)
                    },
                    destructive = true
                )
            }

            item { Spacer(Modifier.height(32.dp)) }

            // Easter egg message
            item {
                Text(
                    "There are few easter eggs hidden in the App",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // DNS Dialog
    if (showDnsDialog) {
        AlertDialog(
            onDismissRequest = { showDnsDialog = false },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Select DNS", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("CLOUDFLARE", "GOOGLE", "ADGUARD", "NONE").forEach { dns ->
                        Text(
                            dns,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDnsDialog = false
                                }
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDnsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Accent Color Dialog
    if (showAccentColorDialog) {
        AlertDialog(
            onDismissRequest = { showAccentColorDialog = false },
            containerColor = Color(0xFF1a1a1a),
            title = { Text("Select Accent Color", color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Purple", "Ocean", "Emerald", "Sunset",
                        "Rose", "Midnight", "Crimson", "Gold", "Saikou"
                    ).forEach { color ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setColorScheme(color.lowercase())
                                    showAccentColorDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                color,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (colorScheme.equals(color, ignoreCase = true)) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccentColorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF8B7AFF), // Purple-blue like Saikou
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    leftIcon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (leftIcon != null) {
                Icon(
                    leftIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    title,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        content()
    }
}

@Composable
private fun SettingNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    title,
                    fontSize = 16.sp,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )

            Text(
                title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                uncheckedTrackColor = Color(0xFF2a2a2a)
            )
        )
    }
}

@Composable
private fun IconToggle(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2a2a2a))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

