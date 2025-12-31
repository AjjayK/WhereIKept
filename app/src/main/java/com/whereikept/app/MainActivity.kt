package com.whereikept.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.whereikept.app.ui.MainScreen
import com.whereikept.app.ui.screens.ModelDownloadScreen
import com.whereikept.app.ui.theme.WhereIKeptTheme
import com.whereikept.app.viewmodel.GemmaDownloadViewModel
import com.whereikept.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val gemmaDownloadViewModel: GemmaDownloadViewModel by viewModels()
    
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
                            MainScreen(viewModel = viewModel)
                        }
                    }
                }
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
