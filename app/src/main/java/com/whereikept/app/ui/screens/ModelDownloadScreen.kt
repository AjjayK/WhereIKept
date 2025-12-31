package com.whereikept.app.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereikept.app.data.ModelDownloadStatusType
import com.whereikept.app.ui.components.GemmaModelDownloadButton
import com.whereikept.app.viewmodel.GemmaDownloadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

private const val TAG = "ModelDownloadScreen"

/**
 * Screen for downloading the Gemma AI model.
 *
 * Implements the complete HuggingFace OAuth + Agreement flow:
 * 1. Check if model is publicly accessible
 * 2. If gated, check for valid OAuth token
 * 3. Request OAuth token if needed
 * 4. After OAuth, test if URL is accessible with token
 * 5. If 403, prompt user to accept model license agreement
 * 6. Open model page in Custom Tab for agreement acceptance
 * 7. Retry download after tab closes
 *
 * Based on Google AI Edge Gallery's implementation.
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

    // State management
    var isCheckingToken by remember { mutableStateOf(false) }
    var showAgreementSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Get auth helper from ViewModel (survives activity pauses and config changes)
    val authHelper = viewModel.authHelper

    // Model URL for extracting agreement URL
    val modelUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"
    val agreementUrl = modelUrl.substringBefore("/resolve/")

    // Function to start download with optional access token
    val startDownload: (String?) -> Unit = { accessToken ->
        Log.i(TAG, "Starting download with token: ${if (accessToken != null) "present" else "null"}")
        if (accessToken != null) {
            viewModel.setAccessToken(accessToken)
        }
        viewModel.startDownload()
        isCheckingToken = false
    }

    // Launcher for opening agreement in Custom Tab
    // When the tab is closed, retry the download
    val agreementAckLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "User closed the agreement browser tab. Retrying download...")
        coroutineScope.launch {
            // Give a moment for HuggingFace to process the agreement
            kotlinx.coroutines.delay(500)
            startDownload(viewModel.accessToken.value)
        }
    }

    // Activity result launcher for OAuth
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "OAuth result received with code: ${result.resultCode}")
        coroutineScope.launch {
            authHelper.handleAuthResult(
                result = result,
                onSuccess = { token ->
                    Log.i(TAG, "Access token obtained. Testing if model needs agreement acknowledgement...")
                    viewModel.setAccessToken(token)

                    // Launch a new coroutine to test the URL (suspend function)
                    coroutineScope.launch(Dispatchers.IO) {
                        // After getting token, check if we can access the model
                        // If we get 403, it means the user needs to accept the agreement
                        val responseCode = viewModel.testModelUrlAccess(accessToken = token)

                        withContext(Dispatchers.Main) {
                            if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                                Log.i(TAG, "Model needs user agreement acknowledgement (HTTP 403)")
                                isCheckingToken = false
                                showAgreementSheet = true
                            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                                Log.i(TAG, "Model is accessible with token (HTTP 200). Starting download...")
                                startDownload(token)
                            } else {
                                Log.e(TAG, "Unexpected response code after OAuth: $responseCode")
                                isCheckingToken = false
                            }
                        }
                    }
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
        Log.i(TAG, "Initiating OAuth flow")
        val authIntent = authHelper.getAuthorizationIntent()
        authLauncher.launch(authIntent)
    }

    // Main download button click handler
    // Follows Google AI Edge Gallery's pattern exactly
    val handleDownloadClick: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            isCheckingToken = true
            Log.i(TAG, "Download button clicked, checking authentication requirements")

            // STEP 1: Check if model is publicly accessible (no auth needed)
            Log.i(TAG, "Testing if model is publicly accessible...")
            val publicResponseCode = viewModel.testModelUrlAccess(accessToken = null)

            if (publicResponseCode == HttpURLConnection.HTTP_OK) {
                // Model is public, no auth needed
                Log.i(TAG, "Model is publicly accessible (HTTP $publicResponseCode), starting download without auth")
                withContext(Dispatchers.Main) {
                    startDownload(null)
                }
            } else {
                // Model requires authentication
                Log.i(TAG, "Model requires authentication (HTTP $publicResponseCode)")

                // STEP 2: Check if we have a valid stored token
                val hasValidToken = viewModel.hasValidToken()

                if (hasValidToken) {
                    // STEP 3: Test if the stored token works
                    Log.i(TAG, "Found stored token, testing validity...")
                    val accessToken = viewModel.accessToken.value
                    val authResponseCode = viewModel.testModelUrlAccess(accessToken = accessToken)

                    if (authResponseCode == HttpURLConnection.HTTP_OK) {
                        // Token works, start download
                        Log.i(TAG, "Stored token is valid (HTTP $authResponseCode), starting download")
                        withContext(Dispatchers.Main) {
                            startDownload(accessToken)
                        }
                    } else if (authResponseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        // Token works but user hasn't accepted agreement
                        Log.i(TAG, "Stored token valid but model needs agreement acknowledgement (HTTP 403)")
                        withContext(Dispatchers.Main) {
                            isCheckingToken = false
                            showAgreementSheet = true
                        }
                    } else {
                        // Token doesn't work, clear it and request new one
                        Log.i(TAG, "Stored token is invalid (HTTP $authResponseCode), clearing and requesting new token")
                        viewModel.clearStoredToken()
                        withContext(Dispatchers.Main) {
                            startOAuthFlow()
                        }
                    }
                } else {
                    // STEP 4: No valid token, start OAuth
                    Log.i(TAG, "No valid token found, starting OAuth flow")
                    withContext(Dispatchers.Main) {
                        startOAuthFlow()
                    }
                }
            }
        }
        Unit
    }

    // Navigate back when download completes
    LaunchedEffect(downloadStatus?.status) {
        if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Download AI Model",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This app uses Google Gemma 3N for AI-powered extraction. The model needs to be downloaded once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Model Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                text = "Size",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "~3.4 GB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Provider",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "HuggingFace",
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
                        text = "Checking access...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Instructions (scrollable)
            if (!isModelReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Important message at the top
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Important",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Important: You must accept the model license",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Text(
                            text = "If prompted, you'll be taken to HuggingFace to review and accept the model agreement. Simply close the browser tab after accepting to continue the download.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )

                        // What happens next section at the bottom
                        Text(
                            text = "What happens next?",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        InstructionStep(
                            number = "1",
                            text = "You'll be asked to sign in to HuggingFace"
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

                        Text(
                            text = "Download happens only once. The model will be saved on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Agreement Acknowledgement Bottom Sheet
    // This is shown when the user has authenticated but hasn't accepted the model license
    if (showAgreementSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAgreementSheet = false
                isCheckingToken = false
            },
            sheetState = sheetState,
            modifier = Modifier.wrapContentHeight()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Acknowledge User Agreement",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "This is a gated model. You need to accept the user agreement on HuggingFace before you can download it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    "When you click the button below, we'll open the model page in your browser. Please review and accept the agreement, then close the browser tab to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        Log.i(TAG, "Opening agreement URL in Custom Tab: $agreementUrl")
                        // Open the model page in a Custom Tab
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.intent.data = agreementUrl.toUri()
                        agreementAckLauncher.launch(customTabsIntent.intent)

                        // Dismiss the sheet
                        showAgreementSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open User Agreement")
                }

                TextButton(
                    onClick = {
                        showAgreementSheet = false
                        isCheckingToken = false
                    }
                ) {
                    Text("Cancel")
                }
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
