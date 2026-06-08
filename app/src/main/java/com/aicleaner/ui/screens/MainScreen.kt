package com.aicleaner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.text.input.ImeAction
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
    onRequestPermission: () -> Unit,
    onStartPremiumPurchase: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val pendingActions by viewModel.pendingActions.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val quotaRemaining by viewModel.quotaRemaining.collectAsState()
    val quotaTotal by viewModel.quotaTotal.collectAsState()
    val isAgentRunning = uiState is MainViewModel.UiState.AgentRunning
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                BeresinHeader(
                    isPremium = isPremium,
                    quotaRemaining = quotaRemaining,
                    quotaTotal = quotaTotal,
                    isAgentRunning = isAgentRunning,
                    hasMessages = chatMessages.isNotEmpty(),
                    onPremiumClick = { showPremiumDialog = true },
                    onClearChat = { viewModel.clearChat() },
                    onSettingsClick = { showSettingsDialog = true }
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        !hasStoragePermission -> PermissionScreen(onRequestPermission)
                        else -> {
                            when (uiState) {
                                is MainViewModel.UiState.Loading -> {
                                    LoadingState()
                                }
                                else -> {
                                    ChatScreen(
                                        messages = chatMessages,
                                        suggestions = suggestions,
                                        pendingActions = pendingActions,
                                        hasApiKey = viewModel.hasApiKey(),
                                        isOnboarding = uiState is MainViewModel.UiState.Onboarding,
                                        isPremium = isPremium,
                                        quotaRemaining = quotaRemaining,
                                        quotaTotal = quotaTotal,
                                        onSendMessage = { viewModel.runAgent(it) },
                                        onSetName = { viewModel.setUserName(it) },
                                        onSuggestionTap = { viewModel.onSuggestionTap(it) },
                                        onConfirmAction = { viewModel.confirmPendingAction(it) },
                                        onCancelAction = { viewModel.cancelPendingAction(it) },
                                        onUpgrade = { showPremiumDialog = true },
                                        isAgentRunning = isAgentRunning,
                                        onCancel = { viewModel.cancelAgent() }
                                    )
                                }
                            }
                        }
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

    if (showPremiumDialog) {
        PremiumDialog(
            installId = viewModel.getInstallId(),
            onStartBilling = onStartPremiumPurchase,
            onDismiss = { showPremiumDialog = false },
            onSaveToken = {
                viewModel.savePremiumToken(it)
                showPremiumDialog = false
            }
        )
    }
}

@Composable
fun BeresinHeader(
    isPremium: Boolean,
    quotaRemaining: Int,
    quotaTotal: Int,
    isAgentRunning: Boolean,
    hasMessages: Boolean,
    onPremiumClick: () -> Unit,
    onClearChat: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Primary, AccentCyan, AccentOrange)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Beresin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        text = if (isAgentRunning) "Running" else "Ready",
                        color = if (isAgentRunning) AccentOrange else Primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (isPremium) "Premium active" else "$quotaRemaining/$quotaTotal chats today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HeaderIconButton(
                icon = Icons.Default.WorkspacePremium,
                selected = isPremium,
                contentDescription = "Premium",
                onClick = onPremiumClick
            )
            if (hasMessages) {
                HeaderIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "New chat",
                    onClick = onClearChat
                )
            }
            HeaderIconButton(
                icon = Icons.Default.Tune,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) AccentOrange.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
            )
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = Primary
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Booting Dora",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== PERMISSION SCREEN ====================

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Primary.copy(alpha = 0.22f), AccentCyan.copy(alpha = 0.16f))
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Storage access",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Dora butuh akses file supaya bisa scan, sortir, dan ngerapihin storage lo.",
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
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Izinkan Akses", fontSize = 16.sp)
        }
    }
}

// ==================== CHAT SCREEN ====================

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    suggestions: List<String>,
    pendingActions: List<com.aicleaner.tools.PendingToolAction>,
    hasApiKey: Boolean,
    isOnboarding: Boolean,
    isPremium: Boolean,
    quotaRemaining: Int = 20,
    quotaTotal: Int = 20,
    onSendMessage: (String) -> Unit,
    onSetName: (String) -> Unit,
    onSuggestionTap: (String) -> Unit,
    onConfirmAction: (String) -> Unit,
    onCancelAction: (String) -> Unit,
    onUpgrade: () -> Unit,
    isAgentRunning: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatHero(isOnboarding = isOnboarding)
                }
            }

            items(messages) { message ->
                when (message) {
                    is ChatMessage.User -> {
                        UserBubble(text = message.text)
                    }
                    is ChatMessage.Agent -> {
                        AgentBubble(
                            text = message.text,
                            steps = message.steps,
                            success = message.success,
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Beresin", text))
                                Toast.makeText(context, "Disalin!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    is ChatMessage.System -> {
                        SystemMessage(text = message.text)
                    }
                }
            }

            // Agent running indicator
            if (isAgentRunning) {
                item {
                    AgentTypingIndicator()
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PendingActionPanel(
                    actions = pendingActions,
                    onConfirm = onConfirmAction,
                    onCancel = onCancelAction
                )

                AnimatedVisibility(
                    visible = isAgentRunning && onCancel != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    RunningControl(onCancel = { onCancel?.invoke() })
                }

                AnimatedVisibility(
                    visible = suggestions.isNotEmpty() && !isAgentRunning,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut()
                ) {
                    SuggestionChips(
                        suggestions = suggestions,
                        onTap = onSuggestionTap
                    )
                }

                QuotaBar(
                    remaining = quotaRemaining,
                    total = quotaTotal,
                    isPremium = isPremium,
                    onUpgrade = onUpgrade
                )

                if (isOnboarding) {
                    NameInputField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSubmit = {
                            if (inputText.isNotBlank()) {
                                onSetName(inputText.trim())
                                inputText = ""
                            }
                        }
                    )
                } else {
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
        }
    }
}

@Composable
private fun EmptyChatHero(isOnboarding: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Primary.copy(alpha = 0.22f), AccentOrange.copy(alpha = 0.2f))
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            if (isOnboarding) "Kenalan dulu" else "Command center storage lo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isOnboarding) {
                "Dora bakal pakai nama ini buat ngobrol lebih natural."
            } else {
                "Scan, sortir, rapihin, dan eksekusi file tetap minta izin lo dulu."
            },
            modifier = Modifier.widthIn(max = 310.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RunningControl(onCancel: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Dora lagi jalanin request",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onCancel) {
                Text("Stop")
            }
        }
    }
}

// ==================== QUOTA BAR ====================

@Composable
fun QuotaBar(
    remaining: Int,
    total: Int,
    isPremium: Boolean,
    onUpgrade: () -> Unit
) {
    val safeTotal = total.coerceAtLeast(1)
    val progress = if (isPremium) 1f else remaining.toFloat() / safeTotal.toFloat()
    val color = when {
        isPremium -> AccentOrange
        remaining <= 3 -> MaterialTheme.colorScheme.error
        remaining <= 10 -> AccentOrange
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPremium) Icons.Default.WorkspacePremium else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPremium) "Premium aktif" else "Sisa $remaining chat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (!isPremium && remaining <= 3) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onUpgrade,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Upgrade", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun PendingActionPanel(
    actions: List<com.aicleaner.tools.PendingToolAction>,
    onConfirm: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    if (actions.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.26f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            action.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        action.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onConfirm(action.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Izinkan")
                        }
                        OutlinedButton(
                            onClick = { onCancel(action.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Batal")
                        }
                    }
                }
            }
        }
    }
}

// ==================== SUGGESTION CHIPS ====================

@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onTap: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                text = suggestion,
                onClick = { onTap(suggestion) }
            )
        }
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    val icon = remember(text) { suggestionIcon(text) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun suggestionIcon(text: String): ImageVector {
    val lower = text.lowercase()
    return when {
        lower.contains("scan") -> Icons.Default.Radar
        lower.contains("duplikat") -> Icons.Default.ContentCopy
        lower.contains("rapih") || lower.contains("folder") -> Icons.Default.FolderCopy
        lower.contains("bersih") || lower.contains("sampah") || lower.contains("clean") -> Icons.Default.CleaningServices
        lower.contains("detail") -> Icons.AutoMirrored.Filled.ListAlt
        else -> Icons.Default.AutoAwesome
    }
}

// ==================== NAME INPUT ====================

@Composable
fun NameInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                        "Ketik nama lo...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSubmit,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Submit",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== CHAT INPUT ====================

@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .imePadding(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isEnabled) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = {
                    Text(
                        if (isEnabled) "Kasih perintah ke Dora..." else "Tunggu Dora selesai...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                enabled = isEnabled,
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = isEnabled && value.isNotBlank(),
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== CHAT BUBBLES ====================

@Composable
fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 292.dp),
            shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            shadowElevation = 3.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // User avatar
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun AgentBubble(
    text: String,
    steps: List<AgentStep> = emptyList(),
    success: Boolean = true,
    onCopy: ((String) -> Unit)? = null
) {
    // Slide-in animation
    val offsetX by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "slideIn"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = -offsetX),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Primary.copy(alpha = 0.95f), AccentCyan.copy(alpha = 0.82f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier.widthIn(max = 302.dp),
            shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                // Show steps summary if there are tool calls
                if (steps.isNotEmpty()) {
                    val toolSteps = steps.filter { it.type == StepType.TOOL_CALL }
                    if (toolSteps.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "${toolSteps.size} operasi",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Main response text
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                // Success/failure indicator
                if (!success) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Selesai dengan catatan",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }

                // Copy button
                if (onCopy != null && text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onCopy(text) },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
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

@Composable
fun SystemMessage(text: String) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AgentTypingIndicator() {
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

    // Bouncing dots animation
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotBounce"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            AccentCyan.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Bouncing dots
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.3f + (0.7f * ((dotScale + index * 0.3f) % 1f))
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Dora lagi mikir...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== SETTINGS DIALOG ====================

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
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = Primary
            )
        },
        title = {
            Text(
                "Pengaturan AI",
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Provider",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                val providers = listOf(
                    "mimo" to "Beresin MiMo",
                    "openai" to "OpenAI GPT",
                    "claude" to "Claude",
                    "deepseek" to "DeepSeek",
                    "custom" to "Custom API"
                )

                providers.forEach { (id, label) ->
                    FilterChip(
                        selected = provider == id,
                        onClick = { provider = id },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                providerIcon(id),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = provider == id,
                            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            selectedBorderColor = Primary.copy(alpha = 0.42f)
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (opsional)") },
                    placeholder = {
                        Text(when (provider) {
                            "mimo" -> "MiMo-2.5-Pro"
                            "openai" -> "gpt-4o"
                            "claude" -> "claude-sonnet-4-20250514"
                            "deepseek" -> "deepseek-chat"
                            else -> "model-name"
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                if (provider == "mimo" || provider == "custom") {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(if (provider == "mimo") "Server URL" else "API URL") },
                        placeholder = {
                            Text(if (provider == "mimo") "http://your-beresin-server:3000"
                                 else "https://api.example.com/v1")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                if (provider != "mimo" || baseUrl.contains("https://")) {
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                ) {
                    Text(
                        when (provider) {
                            "mimo" -> "User tidak perlu API key. Server Beresin yang pegang provider key, quota, dan premium."
                            "openai" -> "Get key: platform.openai.com"
                            "claude" -> "Get key: console.anthropic.com"
                            "deepseek" -> "Get key: platform.deepseek.com"
                            else -> "Masukkan URL dan API key custom provider"
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(provider, key, model, baseUrl) },
                enabled = when (provider) {
                    "mimo" -> true
                    else -> key.isNotBlank()
                },
                shape = RoundedCornerShape(16.dp)
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

private fun providerIcon(provider: String): ImageVector {
    return when (provider) {
        "mimo" -> Icons.Default.AutoAwesome
        "openai" -> Icons.Default.Psychology
        "claude" -> Icons.Default.ChatBubble
        "deepseek" -> Icons.AutoMirrored.Filled.ManageSearch
        else -> Icons.Default.Api
    }
}

@Composable
fun PremiumDialog(
    installId: String,
    onStartBilling: () -> Unit,
    onDismiss: () -> Unit,
    onSaveToken: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = AccentOrange
            )
        },
        title = {
            Text(
                "Beresin Premium",
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PremiumBenefit("Kuota chat lebih longgar")
                PremiumBenefit("Prioritas server MiMo")
                PremiumBenefit("Token divalidasi server, bukan cuma disimpan di app")
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Premium token") },
                    placeholder = { Text("purchase-token / dev token") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(18.dp)
                )
                Text(
                    "Install ID: ${installId.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onStartBilling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Beli via Google Play")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveToken(token) },
                enabled = token.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Aktifkan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Nanti")
            }
        }
    )
}

@Composable
private fun PremiumBenefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AccentOrange.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== ERROR SCREEN ====================

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
            tint = MaterialTheme.colorScheme.error
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
