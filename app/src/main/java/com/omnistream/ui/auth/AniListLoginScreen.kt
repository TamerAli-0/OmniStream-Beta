package com.omnistream.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.omnistream.data.anilist.AniListAuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniListLoginScreen(
    navController: NavController,
    onSuccess: () -> Unit = { navController.popBackStack() }
) {
    val context = LocalContext.current
    val authManager = remember { AniListAuthManager(context) }

    // Poll for login status (check every 500ms)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (authManager.isLoggedIn()) {
                // Successfully connected, close this screen
                onSuccess()
                break
            }
        }
    }

    // Launch browser on composition
    LaunchedEffect(Unit) {
        try {
            val authUrl = authManager.getAuthUrl()
            android.util.Log.d("AniListLogin", "Opening browser with URL: $authUrl")

            // Use Chrome Custom Tabs (like Saikou)
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(context, Uri.parse(authUrl))

            // User will be redirected back to the app via deep link
            // MainActivity will handle the deep link and save the token
        } catch (e: Exception) {
            android.util.Log.e("AniListLogin", "Failed to open browser", e)

            // Fallback to regular browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authManager.getAuthUrl()))
                context.startActivity(intent)
            } catch (e2: Exception) {
                android.util.Log.e("AniListLogin", "Failed to open browser fallback", e2)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AniList Login") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Opening browser...",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Log in with your AniList account in the browser, then return to the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
