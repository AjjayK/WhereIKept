package com.whereikept.app.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereikept.app.ui.components.GemmaModelDownloadButton
import com.whereikept.app.viewmodel.GemmaDownloadViewModel
import kotlinx.coroutines.launch

private const val TAG = "ModelDownloadScreen"

/**
 * Screen for downloading the Gemma AI model.
 *
 * Shows:
 * - Model information
 * - Download button with progress
 * - Instructions for HuggingFace authentication
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onBackClick: () -> Unit,
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GemmaDownloadViewModel = viewModel()
) {
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val isModelReady = viewModel.isModelReady()
    val coroutineScope = rememberCoroutineScope()

    // State for checking token
    var isCheckingToken by remember { mutableStateOf(false) }

    // Get auth helper from ViewModel (survives activity pauses and config changes)
    val authHelper = viewModel.authHelper

    // Activity result launcher for OAuth
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "OAuth result received with code: ${result.resultCode}")
        coroutineScope.launch {
            authHelper.handleAuthResult(
                result = result,
                onSuccess = { token ->
                    Log.i(TAG, "Access token obtained, starting download")
                    viewModel.setAccessToken(token)
                    viewModel.startDownload()
                    isCheckingToken = false
                },
                onError = { error ->
                    Log.e(TAG, "OAuth error: $error")
                    isCheckingToken = false
                }
            )
        }
    }

    // Function to start OAuth flow
    val startOAuthFlow = {
        Log.i(TAG, "No valid token found, initiating OAuth flow")
        val authIntent = authHelper.getAuthorizationIntent()
        authLauncher.launch(authIntent)
    }

    // Function to handle download button click (following Google AI Edge Gallery pattern)
    val handleDownloadClick: () -> Unit = {
        coroutineScope.launch {
            isCheckingToken = true
            Log.i(TAG, "Download button clicked, checking authentication requirements")

            // STEP 1: Try accessing the model URL without authentication
            Log.i(TAG, "Testing if model is publicly accessible...")
            val publicResponseCode = viewModel.testModelUrlAccess(accessToken = null)

            if (publicResponseCode == 200) {
                // Model is public, no auth needed
                Log.i(TAG, "Model is publicly accessible (HTTP $publicResponseCode), starting download without auth")
                viewModel.startDownload()
                isCheckingToken = false
            } else {
                Log.i(TAG, "Model requires authentication (HTTP $publicResponseCode)")

                // STEP 2: Check if we have a valid stored token
                val hasValidToken = viewModel.hasValidToken()

                if (hasValidToken) {
                    // STEP 3: Test if the stored token works
                    Log.i(TAG, "Found stored token, testing validity...")
                    val accessToken = viewModel.accessToken.value
                    val authResponseCode = viewModel.testModelUrlAccess(accessToken = accessToken)

                    if (authResponseCode == 200) {
                        // Token works, start download
                        Log.i(TAG, "Stored token is valid (HTTP $authResponseCode), starting download")
                        viewModel.startDownload()
                        isCheckingToken = false
                    } else {
                        // Token doesn't work, clear it and request new one
                        Log.i(TAG, "Stored token is invalid (HTTP $authResponseCode), clearing and requesting new token")
                        viewModel.clearStoredToken()
                        startOAuthFlow()
                    }
                } else {
                    // No valid token, start OAuth
                    startOAuthFlow()
                }
            }
        }
        Unit
    }

    // Navigate back when download completes
    LaunchedEffect(downloadStatus?.status) {
        if (downloadStatus?.status == com.whereikept.app.data.ModelDownloadStatusType.SUCCEEDED) {
            onDownloadComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Model Setup") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Text(
                text = "Download AI Model",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Model info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "About the AI Model",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "This app uses Google's Gemma 3N model to understand your voice commands and extract item locations. The model runs entirely on your device for privacy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Gemma 3N E2B",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (isModelReady) "Ready" else "Not Downloaded",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isModelReady)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Download button
            GemmaModelDownloadButton(
                downloadStatus = downloadStatus,
                onDownloadClick = handleDownloadClick,
                onCancelClick = {
                    viewModel.cancelDownload()
                    isCheckingToken = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingToken
            )

            // Show checking token indicator
            if (isCheckingToken) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Checking authentication...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Instructions
            if (!isModelReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "What happens next?",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        InstructionStep(
                            number = "1",
                            text = "You'll be asked to sign in to HuggingFace (if needed)"
                        )

                        InstructionStep(
                            number = "2",
                            text = "Accept the model usage agreement"
                        )

                        InstructionStep(
                            number = "3",
                            text = "Download will start automatically"
                        )

                        InstructionStep(
                            number = "4",
                            text = "Once complete, you can start using the app!"
                        )

                        Text(
                            text = "Note: Download may take several minutes depending on your connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer note
            if (!isModelReady) {
                Text(
                    text = "Download happens only once. The model will be saved on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
