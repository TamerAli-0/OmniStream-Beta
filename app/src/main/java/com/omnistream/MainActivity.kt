package com.omnistream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.omnistream.data.anilist.AniListApi
import com.omnistream.data.anilist.AniListAuthManager
import kotlinx.coroutines.withContext
import com.omnistream.ui.MainViewModel
import com.omnistream.ui.MainViewModel.StartDestination
import com.omnistream.ui.navigation.OmniNavigation
import com.omnistream.ui.theme.AppColorScheme
import com.omnistream.ui.theme.DarkModeOption
import com.omnistream.ui.theme.OmniStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var authManager: AniListAuthManager

    @Inject
    lateinit var aniListApi: AniListApi

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Handle deep link if coming from AniList OAuth
        handleDeepLink(intent)

        enableEdgeToEdge()

        // Configure true edge-to-edge - content goes under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Make status bar and nav bar fully transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            // Make system bars transparent and overlay content
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Hide nav buttons by default - swipe up to show temporarily
            hide(WindowInsetsCompat.Type.navigationBars())
            // Keep status bar visible but transparent
            show(WindowInsetsCompat.Type.statusBars())

            // Light status bar icons (white text) for dark theme
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val colorSchemeKey by mainViewModel.colorScheme.collectAsState()
            val darkModeKey by mainViewModel.darkMode.collectAsState()

            val appColorScheme = AppColorScheme.fromKey(colorSchemeKey)
            val darkModeOption = DarkModeOption.fromKey(darkModeKey)
            val isDark = when (darkModeOption) {
                DarkModeOption.DARK -> true
                DarkModeOption.LIGHT -> false
                DarkModeOption.SYSTEM -> isSystemInDarkTheme()
            }

            OmniStreamTheme(darkTheme = isDark, appColorScheme = appColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDest by mainViewModel.startDestination.collectAsState()

                    when (startDest) {
                        is StartDestination.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is StartDestination.AccessGate -> OmniNavigation(startDestination = "access_gate")
                        is StartDestination.Login -> OmniNavigation(startDestination = "login")
                        is StartDestination.Home -> OmniNavigation(startDestination = "home")
                    }

                    // Show update dialog if update is available
                    val showUpdateDialog by mainViewModel.showUpdateDialog.collectAsState()
                    val availableUpdate by mainViewModel.availableUpdate.collectAsState()

                    if (showUpdateDialog && availableUpdate != null) {
                        com.omnistream.ui.components.UpdateDialog(
                            update = availableUpdate!!,
                            onDownload = { mainViewModel.downloadUpdate() },
                            onDismiss = { mainViewModel.dismissUpdateDialog() }
                        )
                    }

                    // Show download progress dialog
                    val downloadProgress by mainViewModel.downloadProgress.collectAsState()
                    com.omnistream.ui.components.DownloadProgressDialog(
                        downloadState = downloadProgress,
                        onDismiss = { mainViewModel.resetDownloadState() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data
        android.util.Log.d("MainActivity", "Deep link received: $data")

        if (data != null && data.scheme == "omnistream" && data.host == "anilist-callback") {
            // Extract authorization code from query parameters
            val code = data.getQueryParameter("code")
            android.util.Log.d("MainActivity", "Authorization code: ${code?.take(10)}...")

            if (!code.isNullOrEmpty()) {
                // Exchange code for access token
                lifecycleScope.launch {
                    try {
                        val token = exchangeCodeForToken(code)
                        if (token != null) {
                            // Save token
                            authManager.saveAccessToken(token)
                            android.util.Log.d("MainActivity", "Token saved successfully")

                            // Fetch and save user info
                            try {
                                val user = aniListApi.getCurrentUser()
                                if (user != null) {
                                    authManager.saveUserInfo(user.id, user.name, user.avatar)
                                    android.util.Log.d("MainActivity", "User info saved: ${user.name}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to fetch user info", e)
                            }

                            // Show success message
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "AniList connected successfully!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            // Refresh home screen to load stats
                            android.util.Log.d("MainActivity", "Triggering stats refresh...")

                            // AniListLoginScreen will auto-close by polling auth status
                        } else {
                            android.util.Log.e("MainActivity", "Failed to exchange code for token")
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Failed to connect AniList",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error during token exchange", e)
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Error: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                android.util.Log.e("MainActivity", "No authorization code in redirect")
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String): String? {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Create OkHttp client with longer timeouts for AniList OAuth
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val formBody = okhttp3.FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", com.omnistream.data.anilist.AniListAuthManager.CLIENT_ID)
                    .add("client_secret", "RRGtDt7zWTq1dyFaHThwyKE3GM7SCrDXxAWplxZ8")
                    .add("redirect_uri", com.omnistream.data.anilist.AniListAuthManager.REDIRECT_URI)
                    .add("code", code)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(com.omnistream.data.anilist.AniListAuthManager.TOKEN_URL)
                    .post(formBody)
                    .build()

                android.util.Log.d("MainActivity", "Exchanging code for token...")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                android.util.Log.d("MainActivity", "Token exchange response code: ${response.code}")
                android.util.Log.d("MainActivity", "Token exchange response: ${responseBody?.take(100)}")

                if (response.isSuccessful && responseBody != null) {
                    // Parse JSON to extract access_token
                    val jsonResponse = org.json.JSONObject(responseBody)
                    val token = jsonResponse.optString("access_token", null)
                    android.util.Log.d("MainActivity", "Token extracted: ${token?.take(10)}...")
                    token
                } else {
                    android.util.Log.e("MainActivity", "Token exchange failed: ${response.code} - $responseBody")
                    null
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("MainActivity", "Timeout during token exchange", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Connection timeout. Check your internet connection.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                null
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Exception during token exchange", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                null
            }
        }
    }
}
