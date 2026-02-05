package com.omnistream.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omnistream.ui.auth.AccessGateScreen
import com.omnistream.ui.auth.AccountOptionsScreen
import com.omnistream.ui.auth.LoginScreen
import com.omnistream.ui.auth.RegisterScreen
import com.omnistream.ui.auth.WelcomeScreen
import com.omnistream.ui.browse.BrowseScreen
import com.omnistream.ui.detail.MangaDetailScreen
import com.omnistream.ui.detail.VideoDetailScreen
import com.omnistream.ui.downloads.DownloadsScreen
import com.omnistream.ui.home.HomeScreen
import com.omnistream.ui.library.LibraryScreen
import com.omnistream.ui.player.PlayerScreen
import com.omnistream.ui.reader.ReaderScreen
import com.omnistream.ui.reader.SaikouReaderScreen
import com.omnistream.ui.search.SearchScreen
import com.omnistream.ui.settings.SettingsScreen
import com.omnistream.ui.auth.AniListLoginScreen
import com.omnistream.ui.utils.hideSystemBarsOnInteraction

/**
 * Navigation routes
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Movies : Screen(
        route = "movies",
        title = "Movies",
        selectedIcon = Icons.Filled.Movie,
        unselectedIcon = Icons.Outlined.Movie
    )

    data object Anime : Screen(
        route = "anime",
        title = "Anime",
        selectedIcon = Icons.Filled.Tv,
        unselectedIcon = Icons.Outlined.Tv
    )

    data object Search : Screen(
        route = "search",
        title = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    )

    data object Library : Screen(
        route = "library",
        title = "Library",
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary
    )

    data object Manga : Screen(
        route = "manga_home",
        title = "Manga",
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    )

    data object Downloads : Screen(
        route = "downloads",
        title = "Downloads",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download
    )
}

// Bottom nav - 5 items with Saikou styling
val bottomNavItems = listOf(
    Screen.Movies,      // Left - Movies/TV shows only
    Screen.Anime,       // Left-center - Anime only
    Screen.Home,        // Center - Everything
    Screen.Library,     // Right-center - Saved content
    Screen.Manga        // Right - Manga only
)

// Auth routes (no bottom nav)
private val authRoutes = setOf("welcome", "access_gate", "account_options", "login", "register")

@Composable
fun OmniNavigation(
    startDestination: String = Screen.Home.route,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Show bottom nav only on main tabs (not auth screens)
    val showBottomNav = currentRoute !in authRoutes && bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    // Calculate responsive bottom padding - no hardcoding!
    val density = LocalDensity.current
    val navigationBarsHeight = WindowInsets.navigationBars.getBottom(density)
    val navBarHeight = 80.dp
    val navBarPadding = 16.dp
    val totalNavSpace = with(density) { navigationBarsHeight.toDp() } + navBarHeight + navBarPadding

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars) // Padding for status bar at top
    ) {
        // Main content with dynamic bottom padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomNav) totalNavSpace else 0.dp)
                .hideSystemBarsOnInteraction() // Hide phone nav on scroll/tap
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200))
                }
            ) {
                // --- Auth screens ---
                composable("welcome") {
                    WelcomeScreen(
                        onCreateAccount = {
                            navController.navigate("access_gate")
                        },
                        onLogin = {
                            navController.navigate("login")
                        }
                    )
                }

                composable("access_gate") {
                    AccessGateScreen(
                        onUnlocked = {
                            navController.navigate("account_options") {
                                popUpTo("access_gate") { inclusive = true }
                            }
                        }
                    )
                }

                composable("account_options") {
                    AccountOptionsScreen(
                        onCreateAccount = {
                            navController.navigate("register")
                        },
                        onSignIn = {
                            navController.navigate("login")
                        }
                    )
                }

                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }

                composable("register") {
                    RegisterScreen(
                        onRegisterSuccess = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                        onNavigateToLogin = {
                            navController.popBackStack()
                        }
                    )
                }

                // --- Main tabs ---
                composable(Screen.Home.route) {
                    HomeScreen(navController = navController)
                }

                composable(Screen.Movies.route) {
                    // Movies/TV Shows only - filter to movie sources
                    BrowseScreen(
                        navController = navController,
                        filterType = "movies" // Only show movie/TV sources
                    )
                }

                composable(Screen.Anime.route) {
                    // Anime only - filter to anime sources
                    BrowseScreen(
                        navController = navController,
                        filterType = "anime" // Only show anime sources
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(navController = navController)
                }

                composable(Screen.Library.route) {
                    LibraryScreen(navController = navController)
                }

                composable(Screen.Manga.route) {
                    // Manga only - filter to manga sources
                    BrowseScreen(
                        navController = navController,
                        filterType = "manga" // Only show manga sources
                    )
                }

                composable(Screen.Downloads.route) {
                    DownloadsScreen(navController = navController)
                }

                // Settings
                composable("settings") {
                    SettingsScreen(
                        navController = navController,
                        onLogout = {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // AniList Login
                composable("anilist_login") {
                    AniListLoginScreen(
                        navController = navController
                    )
                }

                // Manga detail
                composable(
                    route = "manga/{sourceId}/{mangaId}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("mangaId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    MangaDetailScreen(
                        navController = navController,
                        sourceId = backStackEntry.arguments?.getString("sourceId") ?: "",
                        mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
                    )
                }

                // Video detail
                composable(
                    route = "video/{sourceId}/{videoId}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("videoId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    VideoDetailScreen(
                        navController = navController,
                        sourceId = backStackEntry.arguments?.getString("sourceId") ?: "",
                        videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                    )
                }

                // Manga reader
                composable(
                    route = "reader/{sourceId}/{mangaId}/{chapterId}/{title}/{coverUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("mangaId") { type = NavType.StringType },
                        navArgument("chapterId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    SaikouReaderScreen(
                        navController = navController,
                        sourceId = backStackEntry.arguments?.getString("sourceId") ?: "",
                        mangaId = backStackEntry.arguments?.getString("mangaId") ?: "",
                        chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                    )
                }

                // Video player
                composable(
                    route = "player/{sourceId}/{videoId}/{episodeId}/{title}/{coverUrl}",
                    arguments = listOf(
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("episodeId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    PlayerScreen(
                        navController = navController,
                        sourceId = backStackEntry.arguments?.getString("sourceId") ?: "",
                        videoId = backStackEntry.arguments?.getString("videoId") ?: "",
                        episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
                    )
                }
            }
        }

        // Bottom nav - Saikou style (floating, glossy, theme-aware)
        if (showBottomNav) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Gradient fade above nav - responsive height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalNavSpace)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Floating glossy nav bar with animated indicator
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .height(80.dp)
                        .align(Alignment.BottomCenter),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(35.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                    shadowElevation = 12.dp,
                    tonalElevation = 8.dp
                ) {
                    // Nav items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEachIndexed { index, screen ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true

                            // Animated colors - smooth transitions
                            val targetIconColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            val targetTextColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                            val iconColor by androidx.compose.animation.animateColorAsState(
                                targetValue = targetIconColor,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                                label = "iconColor"
                            )
                            val textColor by androidx.compose.animation.animateColorAsState(
                                targetValue = targetTextColor,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                                label = "textColor"
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Animated bubble background for selected item - smooth spring physics
                                val bubbleSize by animateDpAsState(
                                    targetValue = if (selected) 56.dp else 0.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "bubbleSize"
                                )
                                val bubbleAlpha by animateFloatAsState(
                                    targetValue = if (selected) 1f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "bubbleAlpha"
                                )

                                if (bubbleSize > 0.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(bubbleSize)
                                            .clip(CircleShape)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        Color(0xFF6366F1).copy(alpha = bubbleAlpha),
                                                        Color(0xFF4F46E5).copy(alpha = bubbleAlpha)
                                                    )
                                                )
                                            )
                                    )
                                }

                                    Column(
                                    modifier = Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Icon
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title,
                                        tint = iconColor,
                                        modifier = Modifier.size(26.dp)
                                    )

                                    // Label
                                    Text(
                                        text = screen.title.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 1,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
