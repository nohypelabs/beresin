package com.aicleaner.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicleaner.scanner.*
import com.aicleaner.ui.theme.*
import com.aicleaner.viewmodel.ExplorerUiState
import com.aicleaner.viewmodel.ExplorerViewModel

/**
 * Main explorer screen with state-based navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Storage Explorer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    // Back button for non-home states
                    if (uiState !is ExplorerUiState.Welcome &&
                        uiState !is ExplorerUiState.Home) {
                        IconButton(onClick = { viewModel.goHome() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !hasStoragePermission -> PermissionScreen(onRequestPermission)
                else -> {
                    when (val state = uiState) {
                        is ExplorerUiState.Welcome -> WelcomeScreen(
                            onExplore = { viewModel.startScan() }
                        )
                        is ExplorerUiState.Scanning -> ScanningScreen(
                            message = state.message,
                            progress = state.progress
                        )
                        is ExplorerUiState.Home -> HomeScreen(
                            scanResult = state.scanResult,
                            onCategoryClick = { viewModel.selectCategory(it) },
                            onViewSuggestions = { viewModel.viewSuggestions() }
                        )
                        is ExplorerUiState.CategoryDetail -> CategoryDetailScreen(
                            category = state.category,
                            result = state.result,
                            onBack = { viewModel.goHome() }
                        )
                        is ExplorerUiState.Suggestions -> SuggestionsScreen(
                            suggestions = state.suggestions,
                            onSuggestionClick = { viewModel.previewSuggestion(it) },
                            onBack = { viewModel.goHome() }
                        )
                        is ExplorerUiState.Preview -> PreviewScreen(
                            suggestion = state.suggestion,
                            preview = state.preview,
                            onApply = { viewModel.applySuggestion(state.suggestion) },
                            onBack = { viewModel.goHome() }
                        )
                        is ExplorerUiState.Error -> ErrorScreen(
                            message = state.message,
                            onRetry = { viewModel.startScan() }
                        )
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentProvider = viewModel.getProviderType(),
            currentKey = viewModel.getApiKey(),
            currentModel = viewModel.getModel(),
            currentBaseUrl = viewModel.getBaseUrl(),
            onDismiss = { showSettingsDialog = false },
            onSave = { provider, key, model, baseUrl ->
                viewModel.saveConfig(provider, key, model, baseUrl)
                showSettingsDialog = false
            }
        )
    }
}

// ==================== WELCOME SCREEN ====================

@Composable
fun WelcomeScreen(onExplore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Primary, PrimaryDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🔍", fontSize = 60.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Explore Your Storage",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Understand what's inside your phone\nand organize it in seconds.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onExplore,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Explore My Storage", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Your files stay on your device. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ==================== SCANNING SCREEN ====================

@Composable
fun ScanningScreen(message: String, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Scanning animation
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 6.dp,
                progress = { progress }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Scanning Your Storage",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== HOME SCREEN ====================

@Composable
fun HomeScreen(
    scanResult: ScanResult,
    onCategoryClick: (FileCategory) -> Unit,
    onViewSuggestions: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage overview card
        item {
            StorageOverviewCard(scanResult)
        }

        // Health score
        item {
            HealthScoreCard(scanResult.healthScore)
        }

        // Category cards header
        item {
            Text(
                "Categories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Category cards (2 columns)
        val categories = FileCategory.entries.toList()
        items(categories.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { category ->
                    val result = scanResult.categories[category]
                    CategoryCard(
                        category = category,
                        size = result?.totalSizeFormatted ?: "0 B",
                        count = result?.fileCount ?: 0,
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // AI Suggestions header
        if (scanResult.suggestions.isNotEmpty()) {
            item {
                Text(
                    "AI Suggestions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Suggestion cards
            items(scanResult.suggestions) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onClick = { onViewSuggestions() }
                )
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StorageOverviewCard(scanResult: ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Storage Used",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Big percentage
            val usedPercent = scanResult.categories.values.sumOf { it.totalSize }.let { used ->
                val total = getTotalSpace()
                if (total > 0) ((used * 100) / total).toInt() else 0
            }

            Text(
                "$usedPercent%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${scanResult.totalFiles} files • ${scanResult.totalSizeFormatted}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun HealthScoreCard(score: Int) {
    val color = when {
        score >= 80 -> AccentGreen
        score >= 60 -> AccentOrange
        else -> AccentRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$score",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    "Storage Health",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        score >= 80 -> "Your storage is in great shape!"
                        score >= 60 -> "Some cleanup recommended"
                        else -> "Storage needs attention"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: FileCategory,
    size: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(category.emoji, fontSize = 32.sp)

            Column {
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    size,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "$count files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: Suggestion,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            Text(suggestion.emoji, fontSize = 32.sp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${suggestion.fileCount} files • ${suggestion.estimatedSpaceFormatted}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Safety indicator
            Text(suggestion.safetyLevel.emoji, fontSize = 16.sp)
        }
    }
}

// ==================== HELPER FUNCTIONS ====================

private fun getTotalSpace(): Long {
    return try {
        val stat = android.os.StatFs("/sdcard")
        stat.totalBytes
    } catch (e: Exception) {
        0L
    }
}
