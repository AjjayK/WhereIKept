package com.whereikept.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whereikept.app.data.ItemEntity
import com.whereikept.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val allItems by viewModel.allItems.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showItemDetailDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Hide bottom nav when in capture workflow (except IDLE state)
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Capture") },
                    label = { Text("Capture") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    label = { Text("Find") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.itemCount > 0) {
                                    Badge { Text(uiState.itemCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = "Items")
                        }
                    },
                    label = { Text("Items") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    // Use the new enhanced CaptureScreen
                    // Import the CaptureViewModel instead
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val captureViewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.whereikept.app.viewmodel.CaptureViewModel>(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return com.whereikept.app.viewmodel.CaptureViewModel(context.applicationContext as android.app.Application) as T
                            }
                        }
                    )
                    CaptureScreen(viewModel = captureViewModel)
                }
                1 -> SearchScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onItemClick = { item ->
                        selectedItem = item
                        showItemDetailDialog = true
                    }
                )
                2 -> ItemsListScreen(
                    items = allItems,
                    onDeleteItem = { viewModel.deleteItem(it) },
                    onItemClick = { item ->
                        selectedItem = item
                        showItemDetailDialog = true
                    }
                )
                3 -> SettingsScreen()
            }
        }
    }

    // Add Item Dialog
    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { obj, loc, desc ->
                viewModel.addItemManually(obj, loc, desc)
                showAddDialog = false
            }
        )
    }

    // Item Detail Dialog
    if (showItemDetailDialog && selectedItem != null) {
        ItemDetailDialog(
            item = selectedItem!!,
            onDismiss = {
                showItemDetailDialog = false
                selectedItem = null
            }
        )
    }
}

/**
 * @deprecated This screen is no longer used. Recording functionality has been moved to CaptureScreen.
 * The implementation has been removed to avoid compilation errors with refactored MainViewModel.
 * See CaptureScreen for the new enhanced recording workflow.
 */
@Deprecated(
    message = "Use CaptureScreen instead",
    replaceWith = ReplaceWith("CaptureScreen(viewModel)"),
    level = DeprecationLevel.ERROR
)
@Composable
fun RecordScreen(viewModel: MainViewModel, uiState: MainViewModel.UiState) {
    // This screen has been replaced by CaptureScreen
    // Implementation removed as MainViewModel no longer supports recording
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "This screen is deprecated. Use the Capture tab instead.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * @deprecated No longer used. Kept for reference only.
 */
@Deprecated("No longer used", level = DeprecationLevel.WARNING)
@Composable
fun AnimatedRecordingIndicator(isRecording: Boolean, isProcessing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size((80 + index * 20).dp)
                        .scale(if (isRecording) scale else 1f)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.3f - (index * 0.1f)
                            )
                        )
                )
            }
        }
        
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 4.dp
            )
        }
        
        Icon(
            imageVector = when {
                isProcessing -> Icons.Default.Psychology
                isRecording -> Icons.Default.GraphicEq
                else -> Icons.Default.MicNone
            },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = when {
                isRecording -> MaterialTheme.colorScheme.error
                isProcessing -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * @deprecated No longer used. Kept for reference only.
 */
@Deprecated("No longer used", level = DeprecationLevel.WARNING)
@Composable
fun RecordButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) 
            MaterialTheme.colorScheme.error 
        else 
            MaterialTheme.colorScheme.primary,
        label = "buttonColor"
    )
    
    Button(
        onClick = { 
            if (isRecording) onStopRecording() else onStartRecording() 
        },
        enabled = !isProcessing,
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth(0.7f),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        shape = RoundedCornerShape(28.dp)
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when {
                isProcessing -> "Processing..."
                isRecording -> "Stop Recording"
                else -> "Start Recording"
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    uiState: MainViewModel.UiState,
    onItemClick: (ItemEntity) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Where did I put my keys?") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotBlank()) {
                    IconButton(onClick = { 
                        searchText = ""
                        viewModel.searchItems("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.searchItems(searchText) },
            enabled = searchText.isNotBlank() && !uiState.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = uiState.isProcessing) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.searchResults) { item ->
                ItemCard(
                    item = item,
                    onDelete = {},
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun ItemsListScreen(
    items: List<ItemEntity>,
    onDeleteItem: (ItemEntity) -> Unit,
    onItemClick: (ItemEntity) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items saved yet.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                ItemCard(
                    item = item,
                    onDelete = { onDeleteItem(item) },
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun ItemCard(item: ItemEntity, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.objectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.location, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Saved on ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var objectName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = objectName,
                    onValueChange = { objectName = it },
                    label = { Text("Object Name") }
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(objectName, location, description) },
                enabled = objectName.isNotBlank() && location.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailDialog(
    item: ItemEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Item Details",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display image if available
                if (item.taggedImagePath != null || item.imagePath != null) {
                    val imagePathToShow = item.taggedImagePath ?: item.imagePath
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp
                    ) {
                        AsyncImage(
                            model = imagePathToShow,
                            contentDescription = "Item image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Object Name
                DetailField(
                    icon = Icons.Default.Label,
                    label = "Object",
                    value = item.objectName,
                    iconTint = Color(0xFF4F46E5)
                )

                // Location
                DetailField(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    value = item.location,
                    iconTint = Color(0xFFEF4444)
                )

                // Description
                if (item.description.isNotBlank()) {
                    DetailField(
                        icon = Icons.Default.Description,
                        label = "Description",
                        value = item.description,
                        iconTint = Color(0xFF10B981)
                    )
                }

                // Object Attributes
                if (!item.objectAttribute.isNullOrBlank()) {
                    DetailField(
                        icon = Icons.Default.Info,
                        label = "Attributes",
                        value = item.objectAttribute,
                        iconTint = Color(0xFFF59E0B)
                    )
                }

                // Location Parent
                if (!item.locationParent.isNullOrBlank()) {
                    DetailField(
                        icon = Icons.Default.Home,
                        label = "Location Parent",
                        value = item.locationParent,
                        iconTint = Color(0xFF8B5CF6)
                    )
                }

                // Source Type
                DetailField(
                    icon = when(item.sourceType) {
                        "voice" -> Icons.Default.Mic
                        "image" -> Icons.Default.CameraAlt
                        "manual" -> Icons.Default.Edit
                        else -> Icons.Default.Source
                    },
                    label = "Source",
                    value = item.sourceType.replaceFirstChar { it.uppercase() },
                    iconTint = Color(0xFF06B6D4)
                )

                // Timestamp
                DetailField(
                    icon = Icons.Default.CalendarToday,
                    label = "Saved On",
                    value = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        .format(Date(item.timestamp)),
                    iconTint = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5)
                )
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconTint: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO: Navigate to Privacy Policy */ }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PrivacyTip,
                    contentDescription = "Privacy Policy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View our privacy policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* TODO: Navigate to Terms of Service */ }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Terms of Service",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "View our terms of service",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
