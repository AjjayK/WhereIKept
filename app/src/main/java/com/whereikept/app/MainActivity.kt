package com.whereikept.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.whereikept.app.analytics.AnalyticsService
import com.whereikept.app.data.AnalyticsPreferencesManager
import com.whereikept.app.data.AnalyticsRepository
import com.whereikept.app.data.WhereIKeptDatabase
import com.whereikept.app.ui.MainScreen
import com.whereikept.app.ui.dialogs.AnalyticsConsentDialog
import com.whereikept.app.ui.screens.ModelDownloadScreen
import com.whereikept.app.ui.theme.WhereIKeptTheme
import com.whereikept.app.ui.viewmodels.AnalyticsViewModel
import com.whereikept.app.utils.LlmService
import com.whereikept.app.viewmodel.GemmaDownloadViewModel
import com.whereikept.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private val gemmaDownloadViewModel: GemmaDownloadViewModel by viewModels()

    // Analytics dependencies (initialized before ViewModel)
    private val analyticsService by lazy { AnalyticsService.getInstance(applicationContext) }
    private val analyticsRepository by lazy {
        AnalyticsRepository(
            context = applicationContext,
            database = WhereIKeptDatabase.getDatabase(applicationContext),
            analyticsService = analyticsService
        )
    }
    private val preferencesManager by lazy { AnalyticsPreferencesManager(applicationContext) }

    private val analyticsViewModel: AnalyticsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalyticsViewModel(application, analyticsRepository) as T
            }
        }
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Some features may not work without permissions",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        // Initialize analytics and collect startup metrics
        initializeAnalytics()

        setContent {
            WhereIKeptTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember {
                        mutableStateOf(hasRequiredPermissions())
                    }
                    var showDownloadScreen by remember {
                        mutableStateOf(!gemmaDownloadViewModel.isModelReady())
                    }
                    var showConsentDialog by remember { mutableStateOf(false) }

                    // Check if consent dialog needs to be shown
                    LaunchedEffect(Unit) {
                        val hasShown = preferencesManager.hasShownConsentDialog()
                        if (!hasShown) {
                            showConsentDialog = true
                        }
                    }

                    // Consent dialog
                    if (showConsentDialog) {
                        AnalyticsConsentDialog(
                            onAccept = {
                                showConsentDialog = false
                                lifecycleScope.launch {
                                    analyticsRepository.enableAnalytics()
                                    preferencesManager.markConsentDialogShown()
                                    Log.i(TAG, "User accepted analytics consent")
                                }
                            },
                            onDecline = {
                                showConsentDialog = false
                                lifecycleScope.launch {
                                    analyticsRepository.disableAnalytics()
                                    preferencesManager.markConsentDialogShown()
                                    Log.i(TAG, "User declined analytics consent")
                                }
                            }
                        )
                    }

                    when {
                        !permissionsGranted -> {
                            PermissionRequestScreen(
                                onRequestPermissions = {
                                    checkAndRequestPermissions()
                                    permissionsGranted = hasRequiredPermissions()
                                }
                            )
                        }
                        showDownloadScreen -> {
                            ModelDownloadScreen(
                                onBackClick = {
                                    // User can close the app if they don't want to download
                                    finish()
                                },
                                onDownloadComplete = {
                                    showDownloadScreen = false
                                },
                                viewModel = gemmaDownloadViewModel
                            )
                        }
                        else -> {
                            MainScreen(
                                viewModel = viewModel,
                                analyticsViewModel = analyticsViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initializeAnalytics() {
        lifecycleScope.launch {
            try {
                // Wire analytics repository to LlmService for inference tracking
                LlmService.getInstance(applicationContext).setAnalyticsRepository(analyticsRepository)

                // Initialize Firebase Analytics service
                analyticsService.initialize()
                Log.i(TAG, "AnalyticsService initialized")

                // Collect version metrics (one-time per version)
                val versionCode = BuildConfig.VERSION_CODE
                val isNewVersion = analyticsRepository.collectVersionMetricsIfNeeded(versionCode)
                if (isNewVersion) {
                    Log.i(TAG, "Collected version metrics for v${BuildConfig.VERSION_NAME}")
                }

                // Collect device metrics (one-time per device, updated every 30 days)
                val isNewDevice = analyticsRepository.collectDeviceMetricsIfNeeded()
                if (isNewDevice) {
                    Log.i(TAG, "Collected device metrics")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize analytics", e)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: SpeechHelper cleanup is now handled by CaptureViewModel.onCleared()
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Where I Kept needs microphone access to record your voice and camera access to capture images of where you store items.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}
