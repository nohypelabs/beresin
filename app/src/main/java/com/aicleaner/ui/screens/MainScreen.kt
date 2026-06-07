package com.aicleaner.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicleaner.ai.AIEngine
import com.aicleaner.ui.theme.*
import com.aicleaner.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val setupProgress by viewModel.setupProgress.collectAsState()
    val setupMessage by viewModel.setupMessage.collectAsState()

    // API Key dialog state — initialized from ViewModel (persisted)
    var showApiDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(viewModel.getApiKey()) }
    var selectedProvider by remember { mutableStateOf(viewModel.getProvider()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧹", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Beresin")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showApiDialog = true }) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "API Settings",
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
                        is MainViewModel.UiState.Welcome -> WelcomeScreen(
                            onSetup = { viewModel.checkAndSetup() }
                        )
                        is MainViewModel.UiState.SettingUp -> SetupScreen(
                            progress = setupProgress,
                            message = setupMessage
                        )
                        is MainViewModel.UiState.Ready -> ReadyScreen(
                            onScan = {
                                if (!viewModel.hasApiKey()) showApiDialog = true
                                else viewModel.scanStorage()
                            },
                            onOrganize = {
                                if (!viewModel.hasApiKey()) showApiDialog = true
                                else viewModel.organizeDownloads()
                            }
                        )
                        is MainViewModel.UiState.Scanning -> ScanningScreen(state.message)
                        is MainViewModel.UiState.Result -> ResultScreen(
                            analysis = state.analysis,
                            title = "📊 Hasil Analisa",
                            onExecute = { viewModel.executeActions(it) },
                            onBack = { viewModel.resetToReady() }
                        )
                        is MainViewModel.UiState.OrganizationPlan -> ResultScreen(
                            analysis = state.plan,
                            title = "📂 Rencana Organisasi",
                            onExecute = { viewModel.executeActions(it) },
                            onBack = { viewModel.resetToReady() }
                        )
                        is MainViewModel.UiState.Executing -> ExecutingScreen(
                            message = state.message,
                            progress = state.progress
                        )
                        is MainViewModel.UiState.CleanupResult -> CleanupResultScreen(
                            results = state.results,
                            onDone = { viewModel.resetToReady() }
                        )
                        is MainViewModel.UiState.Error -> ErrorScreen(
                            message = state.message,
                            onRetry = { viewModel.resetToReady() }
                        )
                    }
                }
            }
        }
    }

    // API Key Dialog
    if (showApiDialog) {
        ApiKeyDialog(
            currentKey = apiKey,
            currentProvider = selectedProvider,
            onDismiss = { showApiDialog = false },
            onSave = { key, provider ->
                apiKey = key
                selectedProvider = provider
                viewModel.saveAIConfig(provider, key)
                showApiDialog = false
            }
        )
    }
}

// ==================== SCREENS ====================

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Izin Akses Storage",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "AI Cleaner butuh akses storage untuk scan dan bersihkan file HP kamu.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Izinkan Akses", fontSize = 16.sp)
        }
    }
}

@Composable
fun WelcomeScreen(onSetup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧹", fontSize = 80.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "AI Storage Cleaner",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Bersihin storage HP kamu pake AI.\nScan, analisa, dan rapihin otomatis!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onSetup,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Mulai Setup", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Setup sekali aja, butuh internet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SetupScreen(progress: Float, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Setting up...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ReadyScreen(onScan: () -> Unit, onOrganize: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Siap! 🎉",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Mau ngapain?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Scan Storage Button
        ActionCard(
            icon = Icons.Default.Search,
            title = "Scan Storage",
            description = "Analisa semua file, cari sampah & duplikat",
            gradient = Brush.horizontalGradient(listOf(Primary, PrimaryDark)),
            onClick = onScan
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Organize Downloads Button
        ActionCard(
            icon = Icons.Default.FolderOpen,
            title = "Rapihin Download",
            description = "Kategorisasi file di folder Download otomatis",
            gradient = Brush.horizontalGradient(listOf(AccentGreen, AccentGreenLight)),
            onClick = onOrganize
        )
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScanningScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 5.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tunggu sebentar...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ResultScreen(
    analysis: AIEngine.AnalysisResult,
    title: String = "📊 Hasil Analisa",
    onExecute: (List<AIEngine.CleanAction>) -> Unit,
    onBack: () -> Unit
) {
    var selectedActions by remember(analysis) { mutableStateOf(analysis.actions.toSet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(analysis.summary, style = MaterialTheme.typography.bodyMedium)
                if (analysis.totalWaste > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "💾 Total sampah: ${formatSize(analysis.totalWaste)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                }
            }
        }

        // Actions list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(analysis.actions) { action ->
                val isSelected = action in selectedActions
                ActionItem(
                    action = action,
                    isSelected = isSelected,
                    onToggle = {
                        selectedActions = if (isSelected) {
                            selectedActions - action
                        } else {
                            selectedActions + action
                        }
                    }
                )
            }
        }

        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Kembali")
            }
            Button(
                onClick = { onExecute(selectedActions.toList()) },
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedActions.isNotEmpty()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Jalankan (${selectedActions.size})")
            }
        }
    }
}

@Composable
fun ActionItem(
    action: AIEngine.CleanAction,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (action.type) {
                            "delete" -> "🗑️"
                            "move" -> "📁"
                            "organize" -> "📂"
                            else -> "📄"
                        },
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        action.type.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (action.type) {
                            "delete" -> AccentRed
                            "move" -> AccentOrange
                            "organize" -> AccentGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    action.target.substringAfterLast("/").take(50),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (action.reason.isNotEmpty()) {
                    Text(
                        action.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action.sizeBytes > 0) {
                    Text(
                        formatSize(action.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutingScreen(message: String, progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CleaningServices,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Membersihkan...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CleanupResultScreen(results: List<String>, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AccentGreenLight)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎉", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Selesai!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${results.count { it.startsWith("✅") }} berhasil, ${results.count { it.startsWith("❌") }} gagal",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Results list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(results) { result ->
                Text(
                    result,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        // Done button
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Selesai", fontSize = 16.sp)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AccentRed
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Coba Lagi")
        }
    }
}

@Composable
fun ApiKeyDialog(
    currentKey: String,
    currentProvider: AIEngine.Provider,
    onDismiss: () -> Unit,
    onSave: (String, AIEngine.Provider) -> Unit
) {
    var key by remember { mutableStateOf(currentKey) }
    var provider by remember { mutableStateOf(currentProvider) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔑 AI API Settings") },
        text = {
            Column {
                Text(
                    "Pilih AI provider dan masukkan API key:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Provider selection
                Text("Provider:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AIEngine.Provider.entries.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p },
                            label = {
                                Text(when (p) {
                                    AIEngine.Provider.CLAUDE -> "Claude"
                                    AIEngine.Provider.OPENAI -> "GPT"
                                    AIEngine.Provider.GEMINI -> "Gemini"
                                })
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // API Key input (masked by default)
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    when (provider) {
                        AIEngine.Provider.CLAUDE -> "Get key: console.anthropic.com"
                        AIEngine.Provider.OPENAI -> "Get key: platform.openai.com"
                        AIEngine.Provider.GEMINI -> "Get key: makersuite.google.com"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key, provider) },
                enabled = key.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// ==================== HELPERS ====================

fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
