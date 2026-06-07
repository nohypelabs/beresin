package com.aicleaner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicleaner.ai.AgentStep
import com.aicleaner.ai.StepType
import com.aicleaner.ui.theme.*
import com.aicleaner.viewmodel.ChatMessage
import com.aicleaner.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                    // Clear chat button (only show when there are messages)
                    if (chatMessages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
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
                        is MainViewModel.UiState.Welcome -> WelcomeScreen(
                            onStart = { viewModel.checkAndSetup() }
                        )
                        is MainViewModel.UiState.Ready -> ChatScreen(
                            messages = chatMessages,
                            hasApiKey = viewModel.hasApiKey(),
                            onSendMessage = { viewModel.runAgent(it) },
                            onScan = { viewModel.scanStorage() },
                            onOrganize = { viewModel.organizeDownloads() },
                            onDuplicates = { viewModel.findDuplicates() },
                            onCleanJunk = { viewModel.cleanJunk() }
                        )
                        is MainViewModel.UiState.AgentRunning -> ChatScreen(
                            messages = chatMessages,
                            hasApiKey = viewModel.hasApiKey(),
                            onSendMessage = { viewModel.runAgent(it) },
                            onScan = { viewModel.scanStorage() },
                            onOrganize = { viewModel.organizeDownloads() },
                            onDuplicates = { viewModel.findDuplicates() },
                            onCleanJunk = { viewModel.cleanJunk() },
                            isAgentRunning = true,
                            onCancel = { viewModel.cancelAgent() }
                        )
                        is MainViewModel.UiState.AgentResult -> ChatScreen(
                            messages = chatMessages,
                            hasApiKey = viewModel.hasApiKey(),
                            onSendMessage = { viewModel.runAgent(it) },
                            onScan = { viewModel.scanStorage() },
                            onOrganize = { viewModel.organizeDownloads() },
                            onDuplicates = { viewModel.findDuplicates() },
                            onCleanJunk = { viewModel.cleanJunk() }
                        )
                        is MainViewModel.UiState.Error -> ChatScreen(
                            messages = chatMessages,
                            hasApiKey = viewModel.hasApiKey(),
                            onSendMessage = { viewModel.runAgent(it) },
                            onScan = { viewModel.scanStorage() },
                            onOrganize = { viewModel.organizeDownloads() },
                            onDuplicates = { viewModel.findDuplicates() },
                            onCleanJunk = { viewModel.cleanJunk() }
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
            "Beresin butuh akses storage untuk scan dan bersihkan file HP kamu.",
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
fun WelcomeScreen(onStart: () -> Unit) {
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
            "Beresin",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "\"Beresin HP lu!\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "AI-powered storage cleaner.\nScan, analisa, dan rapihin otomatis!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Mulai", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReadyScreen(
    onScan: () -> Unit,
    onOrganize: () -> Unit,
    onDuplicates: () -> Unit,
    onCleanJunk: () -> Unit,
    hasApiKey: Boolean
) {
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

        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tap ⚙️ untuk set API key dulu ya",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action cards
        ActionCard(
            icon = Icons.Default.Search,
            title = "Scan Storage",
            description = "Analisa semua file, cari sampah & duplikat",
            gradient = Brush.horizontalGradient(listOf(Primary, PrimaryDark)),
            onClick = onScan
        )

        Spacer(modifier = Modifier.height(12.dp))

        ActionCard(
            icon = Icons.Default.FolderOpen,
            title = "Rapihin Download",
            description = "Kategorisasi file di Download otomatis",
            gradient = Brush.horizontalGradient(listOf(AccentGreen, AccentGreenLight)),
            onClick = onOrganize
        )

        Spacer(modifier = Modifier.height(12.dp))

        ActionCard(
            icon = Icons.Default.ContentCopy,
            title = "Cari Duplikat",
            description = "Temukan file duplikat yang makan tempat",
            gradient = Brush.horizontalGradient(listOf(AccentOrange, AccentOrangeLight)),
            onClick = onDuplicates
        )

        Spacer(modifier = Modifier.height(12.dp))

        ActionCard(
            icon = Icons.Default.CleaningServices,
            title = "Bersihin Sampah",
            description = "Hapus file .tmp, .cache, .log, APK lama",
            gradient = Brush.horizontalGradient(listOf(AccentRed, AccentRedLight)),
            onClick = onCleanJunk
        )
    }
}

// ==================== CHAT SCREEN ====================

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    hasApiKey: Boolean,
    onSendMessage: (String) -> Unit,
    onScan: () -> Unit,
    onOrganize: () -> Unit,
    onDuplicates: () -> Unit,
    onCleanJunk: () -> Unit,
    isAgentRunning: Boolean = false,
    onCancel: (() -> Unit)? = null,
    errorMessage: String? = null
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show quick actions if no messages yet
            if (messages.isEmpty()) {
                item {
                    QuickActionsRow(
                        onScan = onScan,
                        onOrganize = onOrganize,
                        onDuplicates = onDuplicates,
                        onCleanJunk = onCleanJunk
                    )
                }
            }

            // Chat messages
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    onCopy = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Beresin", text))
                        Toast.makeText(context, "Disalin!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Agent running indicator
            if (isAgentRunning) {
                item {
                    AgentTypingIndicator()
                }
            }

            // Error message inline
            if (errorMessage != null) {
                item {
                    ErrorInlineCard(errorMessage)
                }
            }
        }

        // API key warning
        if (!hasApiKey) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set API key di ⚙️ dulu ya",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Input field
        ChatInputField(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText.trim())
                    inputText = ""
                }
            },
            isEnabled = !isAgentRunning && hasApiKey
        )
    }
}

@Composable
fun QuickActionsRow(
    onScan: () -> Unit,
    onOrganize: () -> Unit,
    onDuplicates: () -> Unit,
    onCleanJunk: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            "🧹 Beresin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Mau beresin apa hari ini?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Quick action chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(
                icon = Icons.Default.Search,
                label = "Scan",
                onClick = onScan,
                modifier = Modifier.weight(1f)
            )
            QuickActionChip(
                icon = Icons.Default.FolderOpen,
                label = "Rapihin",
                onClick = onOrganize,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(
                icon = Icons.Default.ContentCopy,
                label = "Duplikat",
                onClick = onDuplicates,
                modifier = Modifier.weight(1f)
            )
            QuickActionChip(
                icon = Icons.Default.CleaningServices,
                label = "Sampah",
                onClick = onCleanJunk,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Atau ketik pesan di bawah...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onCopy: ((String) -> Unit)? = null
) {
    val isUser = message is ChatMessage.User

    // Slide-in animation
    val offsetX by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = if (isUser) offsetX else -offsetX),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Agent avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Message bubble
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message) {
                    is ChatMessage.User -> {
                        Text(
                            message.text,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is ChatMessage.Agent -> {
                        // Show steps summary if there are tool calls
                        if (message.steps.isNotEmpty()) {
                            val toolSteps = message.steps.filter { it.type == StepType.TOOL_CALL }
                            if (toolSteps.isNotEmpty()) {
                                Text(
                                    "📋 ${toolSteps.size} operasi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        // Main response text
                        Text(
                            message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Success/failure indicator
                        if (!message.success) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "⚠️ Selesai dengan catatan",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange
                            )
                        }

                        // Copy button for agent responses
                        if (onCopy != null && message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { onCopy(message.text) },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Salin",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Salin",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun AgentTypingIndicator() {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Agent avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Beresin lagi mikir...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ErrorInlineCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Oops! Ada error",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = {
                    Text(
                        "Tanya Beresin...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = isEnabled,
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = isEnabled && value.isNotBlank(),
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AgentRunningScreen(
    message: String,
    steps: List<AgentStep>,
    onCancel: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(steps.size) {
        if (steps.isNotEmpty()) {
            listState.animateScrollToItem(steps.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "🤖 AI sedang bekerja...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Steps list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(steps) { step ->
                StepItem(step)
            }
        }

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Batalkan")
        }
    }
}

@Composable
fun StepItem(step: AgentStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                step.success == true -> AccentGreenLight
                step.success == false -> AccentRedLight
                step.type == StepType.TOOL_CALL -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            when {
                step.type == StepType.RESPONSE -> {
                    Text("💬", modifier = Modifier.padding(end = 8.dp))
                }
                step.success == true -> {
                    Text("✅", modifier = Modifier.padding(end = 8.dp))
                }
                step.success == false -> {
                    Text("❌", modifier = Modifier.padding(end = 8.dp))
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                if (step.toolName != null) {
                    Text(
                        step.toolName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    step.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun AgentResultScreen(
    result: com.aicleaner.ai.AgentResult,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (result.success) AccentGreenLight
                    else AccentOrangeLight
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (result.success) "🎉 Selesai!" else "⚠️ Selesai dengan catatan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${result.iterations} langkah • ${result.steps.size} operasi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Final response
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp)
            ) {
                item {
                    Text(
                        result.finalResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp
                    )
                }
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
fun SettingsDialog(
    currentProvider: String,
    currentKey: String,
    currentModel: String,
    currentBaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (provider: String, key: String, model: String, baseUrl: String) -> Unit
) {
    var provider by remember { mutableStateOf(currentProvider) }
    var key by remember { mutableStateOf(currentKey) }
    var model by remember { mutableStateOf(currentModel) }
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Pengaturan AI") },
        text = {
            Column {
                Text(
                    "Pilih AI provider:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Provider selection
                val providers = listOf(
                    "mimo" to "🤖 Xiaomi MiMo",
                    "openai" to "🧠 OpenAI GPT",
                    "claude" to "🎭 Claude",
                    "deepseek" to "🔮 DeepSeek",
                    "custom" to "🔧 Custom API"
                )

                providers.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == id,
                            onClick = { provider = id }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model input
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (opsional)") },
                    placeholder = {
                        Text(when (provider) {
                            "mimo" -> "MiMo-7B-RL"
                            "openai" -> "gpt-4o"
                            "claude" -> "claude-sonnet-4-20250514"
                            "deepseek" -> "deepseek-chat"
                            else -> "model-name"
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Base URL for MiMo / Custom
                if (provider == "mimo" || provider == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API URL") },
                        placeholder = {
                            Text(if (provider == "mimo") "http://your-server:8000/v1"
                                 else "https://api.example.com/v1")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // API Key (not needed for local MiMo)
                if (provider != "mimo" || baseUrl.contains("https://")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    when (provider) {
                        "mimo" -> "💡 MiMo bisa jalan lokal tanpa API key kalau di-host sendiri"
                        "openai" -> "Get key: platform.openai.com"
                        "claude" -> "Get key: console.anthropic.com"
                        "deepseek" -> "Get key: platform.deepseek.com"
                        else -> "Masukkan URL dan API key custom provider"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(provider, key, model, baseUrl) },
                enabled = when (provider) {
                    "mimo" -> baseUrl.isNotBlank() || model.isNotBlank()
                    else -> key.isNotBlank()
                }
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
