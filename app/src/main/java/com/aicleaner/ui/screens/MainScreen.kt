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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Dora avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Dora",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Beresin AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    TextButton(onClick = { showPremiumDialog = true }) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isPremium) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPremium) "Premium" else "Upgrade")
                    }
                    if (chatMessages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    when (uiState) {
                        is MainViewModel.UiState.Loading -> {
                            // Show empty chat while greeting loads
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        else -> {
                            val quotaRemaining by viewModel.quotaRemaining.collectAsState()
                            val quotaTotal by viewModel.quotaTotal.collectAsState()

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
                                isAgentRunning = uiState is MainViewModel.UiState.AgentRunning,
                                onCancel = { viewModel.cancelAgent() }
                            )
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
            "Dora butuh akses storage buat scan dan bersihin file HP lo.",
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
            // Chat messages
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

        PendingActionPanel(
            actions = pendingActions,
            onConfirm = onConfirmAction,
            onCancel = onCancelAction
        )

        // Suggestion chips (contextual)
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

        // Quota display
        QuotaBar(
            remaining = quotaRemaining,
            total = quotaTotal,
            isPremium = isPremium,
            onUpgrade = onUpgrade
        )

        // Input field
        if (isOnboarding) {
            // Name input during onboarding
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
            // Normal chat input
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "💬",
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPremium) "Premium aktif" else "$remaining/$total chat hari ini",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                TextButton(onClick = onUpgrade) {
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            action.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        action.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConfirm(action.id) }) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Izinkan")
                        }
                        OutlinedButton(onClick = { onCancel(action.id) }) {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== NAME INPUT ====================

@Composable
fun NameInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
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
                        "Tanya Dora...",
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

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

// ==================== CHAT BUBBLES ====================

@Composable
fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // User avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiary
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
        horizontalArrangement = Arrangement.Start
    ) {
        // Dora avatar
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

        // Message bubble
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show steps summary if there are tool calls
                if (steps.isNotEmpty()) {
                    val toolSteps = steps.filter { it.type == StepType.TOOL_CALL }
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
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Success/failure indicator
                if (!success) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "⚠️ Selesai dengan catatan",
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

@Composable
fun SystemMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
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
        horizontalArrangement = Arrangement.Start
    ) {
        // Dora avatar
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
        title = { Text("⚙️ Pengaturan AI") },
        text = {
            Column {
                Text(
                    "Pilih AI provider:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val providers = listOf(
                    "mimo" to "🤖 Beresin MiMo",
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
                    singleLine = true
                )

                if (provider == "mimo" || provider == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(if (provider == "mimo") "Server URL" else "API URL") },
                        placeholder = {
                            Text(if (provider == "mimo") "http://your-beresin-server:3000"
                                 else "https://api.example.com/v1")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

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
                        "mimo" -> "💡 User tidak perlu API key. Server Beresin yang pegang provider key, quota, dan premium."
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
                    "mimo" -> true
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
        title = { Text("Upgrade Premium") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Premium unlock unlimited chat normal-use dan prioritas server. Untuk production, token ini diisi dari Google Play Billing purchase token.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Premium token") },
                    placeholder = { Text("purchase-token / dev token") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text(
                    "Install ID: ${installId.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onStartBilling,
                    modifier = Modifier.fillMaxWidth()
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
                enabled = token.isNotBlank()
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
