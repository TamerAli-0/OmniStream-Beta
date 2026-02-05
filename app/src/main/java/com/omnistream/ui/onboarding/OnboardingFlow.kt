package com.omnistream.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingFlow(
    navController: NavController,
    onComplete: (OnboardingPreferences) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    var selectedTheme by remember { mutableStateOf(AppTheme.SYSTEM) }
    var selectedAccent by remember { mutableStateOf(AccentColor.PINK) }
    var selectedLayout by remember { mutableStateOf(LayoutStyle.CARD) }
    var selectedReaderMode by remember { mutableStateOf(DefaultReaderMode.VERTICAL) }
    var enableAmoled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> ThemeSelectionPage(
                    selectedTheme = selectedTheme,
                    enableAmoled = enableAmoled,
                    onThemeSelected = { selectedTheme = it },
                    onAmoledToggle = { enableAmoled = it }
                )
                2 -> AccentColorPage(
                    selectedAccent = selectedAccent,
                    onAccentSelected = { selectedAccent = it }
                )
                3 -> LayoutSelectionPage(
                    selectedLayout = selectedLayout,
                    onLayoutSelected = { selectedLayout = it }
                )
                4 -> ReaderModePage(
                    selectedMode = selectedReaderMode,
                    onModeSelected = { selectedReaderMode = it }
                )
            }
        }

        // Bottom navigation
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip button
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        onComplete(
                            OnboardingPreferences(
                                theme = selectedTheme,
                                accentColor = selectedAccent,
                                layoutStyle = selectedLayout,
                                readerMode = selectedReaderMode,
                                amoledMode = enableAmoled
                            )
                        )
                    }) {
                        Text("Skip")
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val width by animateFloatAsState(
                            if (pagerState.currentPage == index) 32f else 8f,
                            label = "indicator"
                        )
                        Box(
                            modifier = Modifier
                                .width(width.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Next/Finish button
                Button(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage < 4) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                onComplete(
                                    OnboardingPreferences(
                                        theme = selectedTheme,
                                        accentColor = selectedAccent,
                                        layoutStyle = selectedLayout,
                                        readerMode = selectedReaderMode,
                                        amoledMode = enableAmoled
                                    )
                                )
                            }
                        }
                    }
                ) {
                    Text(if (pagerState.currentPage < 4) "Next" else "Finish")
                    Icon(
                        if (pagerState.currentPage < 4) Icons.Default.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PlayCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "Welcome to OmniStream",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Watch anime, read manga, and download content from multiple sources",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(48.dp))

        Text(
            "Let's customize your experience",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ThemeSelectionPage(
    selectedTheme: AppTheme,
    enableAmoled: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onAmoledToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Choose Your Theme",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Select the appearance that suits you best",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        // Theme options
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeOption(
                icon = Icons.Default.Brightness4,
                title = "System Default",
                description = "Follow system theme",
                isSelected = selectedTheme == AppTheme.SYSTEM,
                onClick = { onThemeSelected(AppTheme.SYSTEM) }
            )

            ThemeOption(
                icon = Icons.Default.LightMode,
                title = "Light",
                description = "Always use light theme",
                isSelected = selectedTheme == AppTheme.LIGHT,
                onClick = { onThemeSelected(AppTheme.LIGHT) }
            )

            ThemeOption(
                icon = Icons.Default.DarkMode,
                title = "Dark",
                description = "Always use dark theme",
                isSelected = selectedTheme == AppTheme.DARK,
                onClick = { onThemeSelected(AppTheme.DARK) }
            )
        }

        Spacer(Modifier.height(24.dp))

        // AMOLED toggle
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Contrast, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("AMOLED Mode", fontWeight = FontWeight.Medium)
                    Text("Pure black for OLED screens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                Switch(checked = enableAmoled, onCheckedChange = onAmoledToggle)
            }
        }
    }
}

@Composable
private fun AccentColorPage(
    selectedAccent: AccentColor,
    onAccentSelected: (AccentColor) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Pick Your Accent Color",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Choose the color that makes the app yours",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(48.dp))

        // Color grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            ColorOption(AccentColor.PINK, "Pink\n(Saikou)", Color(0xFFE91E63), selectedAccent == AccentColor.PINK) { onAccentSelected(AccentColor.PINK) }
            ColorOption(AccentColor.PURPLE, "Purple", Color(0xFF9C27B0), selectedAccent == AccentColor.PURPLE) { onAccentSelected(AccentColor.PURPLE) }
            ColorOption(AccentColor.BLUE, "Blue", Color(0xFF2196F3), selectedAccent == AccentColor.BLUE) { onAccentSelected(AccentColor.BLUE) }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            ColorOption(AccentColor.GREEN, "Green", Color(0xFF4CAF50), selectedAccent == AccentColor.GREEN) { onAccentSelected(AccentColor.GREEN) }
            ColorOption(AccentColor.ORANGE, "Orange", Color(0xFFFF9800), selectedAccent == AccentColor.ORANGE) { onAccentSelected(AccentColor.ORANGE) }
            ColorOption(AccentColor.RED, "Red", Color(0xFFF44336), selectedAccent == AccentColor.RED) { onAccentSelected(AccentColor.RED) }
        }
    }
}

@Composable
private fun LayoutSelectionPage(
    selectedLayout: LayoutStyle,
    onLayoutSelected: (LayoutStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Choose Your Layout",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "How do you want to browse content?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LayoutOption(
                icon = Icons.Default.ViewModule,
                title = "Card Layout",
                description = "Large cards with covers (Recommended)",
                isSelected = selectedLayout == LayoutStyle.CARD,
                onClick = { onLayoutSelected(LayoutStyle.CARD) }
            )

            LayoutOption(
                icon = Icons.Default.ViewCompact,
                title = "Compact Layout",
                description = "Smaller cards, more items per row",
                isSelected = selectedLayout == LayoutStyle.COMPACT,
                onClick = { onLayoutSelected(LayoutStyle.COMPACT) }
            )

            LayoutOption(
                icon = Icons.Default.ViewList,
                title = "List Layout",
                description = "Detailed list with descriptions",
                isSelected = selectedLayout == LayoutStyle.LIST,
                onClick = { onLayoutSelected(LayoutStyle.LIST) }
            )
        }
    }
}

@Composable
private fun ReaderModePage(
    selectedMode: DefaultReaderMode,
    onModeSelected: (DefaultReaderMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Default Reading Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "How do you prefer to read manga?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderOption(
                icon = Icons.Default.SwipeVertical,
                title = "Vertical Continuous",
                description = "Scroll down (Webtoon style)",
                isSelected = selectedMode == DefaultReaderMode.VERTICAL,
                onClick = { onModeSelected(DefaultReaderMode.VERTICAL) }
            )

            ReaderOption(
                icon = Icons.Default.ArrowForward,
                title = "Left to Right",
                description = "Horizontal pages (Western comics)",
                isSelected = selectedMode == DefaultReaderMode.LTR,
                onClick = { onModeSelected(DefaultReaderMode.LTR) }
            )

            ReaderOption(
                icon = Icons.Default.ArrowBack,
                title = "Right to Left",
                description = "Horizontal pages (Japanese manga)",
                isSelected = selectedMode == DefaultReaderMode.RTL,
                onClick = { onModeSelected(DefaultReaderMode.RTL) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(32.dp), if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ColorOption(
    color: AccentColor,
    label: String,
    colorValue: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(colorValue),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LayoutOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(32.dp), if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ReaderOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(32.dp), if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

data class OnboardingPreferences(
    val theme: AppTheme,
    val accentColor: AccentColor,
    val layoutStyle: LayoutStyle,
    val readerMode: DefaultReaderMode,
    val amoledMode: Boolean
)

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AccentColor { PINK, PURPLE, BLUE, GREEN, ORANGE, RED }
enum class LayoutStyle { CARD, COMPACT, LIST }
enum class DefaultReaderMode { VERTICAL, LTR, RTL }
