package com.example

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme

class AutoTyperKeyboardService : InputMethodService(),
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var settingsManager: SettingsManager
    private val typingEngine = TypingEngine()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        settingsManager = SettingsManager(this)
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(this@AutoTyperKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@AutoTyperKeyboardService)
            setViewTreeViewModelStoreOwner(this@AutoTyperKeyboardService)

            setContent {
                MyApplicationTheme {
                    AutoTyperKeyboardContent(
                        settingsManager = settingsManager,
                        typingEngine = typingEngine,
                        getInputConnection = { currentInputConnection },
                        onSwitchKeyboard = {
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                        onOpenMainApp = {
                            val intent = Intent(this@AutoTyperKeyboardService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        },
                        onSendKeyEvent = { keyCode ->
                            sendDownUpKeyEvents(keyCode)
                        },
                        onCommitSpace = {
                            currentInputConnection?.commitText(" ", 1)
                        }
                    )
                }
            }
        }
        return composeView
    }

    override fun onDestroy() {
        typingEngine.stopTyping()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
        super.onDestroy()
    }
}

@Composable
fun AutoTyperKeyboardContent(
    settingsManager: SettingsManager,
    typingEngine: TypingEngine,
    getInputConnection: () -> InputConnection?,
    onSwitchKeyboard: () -> Unit,
    onOpenMainApp: () -> Unit,
    onSendKeyEvent: (Int) -> Unit,
    onCommitSpace: () -> Unit
) {
    val savedText by settingsManager.savedText.collectAsState()
    val speedMs by settingsManager.speedMs.collectAsState()
    val typoEnabled by settingsManager.typoEnabled.collectAsState()
    val pauseEnabled by settingsManager.pauseEnabled.collectAsState()

    val typingState by typingEngine.state.collectAsState()
    val currentIndex by typingEngine.currentIndex.collectAsState()
    val totalChars by typingEngine.totalChars.collectAsState()
    val progressPercentage by typingEngine.progressPercentage.collectAsState()
    val statusText by typingEngine.statusText.collectAsState()

    val percentInt = (progressPercentage * 100).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = Color(0xFF211F26), // Dark Sophisticated container
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header: Service Title, Status Badge & Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "AutoTyper IME",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "IME ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                // Status Badge
                StatusChip(state = typingState, statusText = statusText)
            }

            // Text Preview & Progress Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2930) // Dark Surface card
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF49454F).copy(alpha = 0.5f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (savedText.isNotEmpty()) "\"${savedText.take(50)}${if (savedText.length > 50) "..." else ""}\"" else "(No saved text available)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percentInt% COMPLETE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = progressPercentage.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF49454F)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$currentIndex / $totalChars chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "${speedMs}ms | Typos: ${if (typoEnabled) "ON" else "OFF"} | Pauses: ${if (pauseEnabled) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Keyboard Control Buttons (Start, Stop, Pause, Resume)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // START BUTTON
                Button(
                    onClick = {
                        typingEngine.startTyping(
                            connection = getInputConnection(),
                            text = savedText,
                            speedMs = speedMs,
                            typoEnabled = typoEnabled,
                            pauseEnabled = pauseEnabled
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "START", fontWeight = FontWeight.Bold)
                }

                // PAUSE / RESUME BUTTON
                if (typingState == TypingState.Typing) {
                    FilledTonalButton(
                        onClick = { typingEngine.pauseTyping() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF4A4458),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "PAUSE", fontWeight = FontWeight.Bold)
                    }
                } else if (typingState == TypingState.Paused) {
                    FilledTonalButton(
                        onClick = { typingEngine.resumeTyping() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "RESUME", fontWeight = FontWeight.Bold)
                    }
                }

                // STOP BUTTON
                Button(
                    onClick = { typingEngine.stopTyping() },
                    enabled = typingState == TypingState.Typing || typingState == TypingState.Paused,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF2B8B5),
                        contentColor = Color(0xFF601410),
                        disabledContainerColor = Color(0xFF49454F).copy(alpha = 0.3f),
                        disabledContentColor = Color(0xFF938F99)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "STOP", fontWeight = FontWeight.Bold)
                }
            }

            // Quick Key Utilities Bar (Settings, Switch IME, Space, Backspace, Enter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open Main App / Settings
                IconButton(
                    onClick = onOpenMainApp,
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF2B2930)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings App",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Switch Keyboard Input Method
                IconButton(
                    onClick = onSwitchKeyboard,
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF2B2930)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Switch Input Method",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Space Bar
                FilledTonalButton(
                    onClick = onCommitSpace,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF2B2930),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "SPACE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                // Backspace Key
                IconButton(
                    onClick = { onSendKeyEvent(KeyEvent.KEYCODE_DEL) },
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF2B2930)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Enter Key
                IconButton(
                    onClick = { onSendKeyEvent(KeyEvent.KEYCODE_ENTER) },
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(state: TypingState, statusText: String) {
    val (bgColor, textColor, icon) = when (state) {
        TypingState.Idle -> Triple(
            Color(0xFF2B2930),
            Color(0xFFCAC4D0),
            Icons.Default.HourglassEmpty
        )
        TypingState.Typing -> Triple(
            Color(0xFF1B3A22),
            Color(0xFF81C784),
            Icons.Default.PlayArrow
        )
        TypingState.Paused -> Triple(
            Color(0xFF3E2723),
            Color(0xFFFFB74D),
            Icons.Default.Pause
        )
        TypingState.Stopped -> Triple(
            Color(0xFF3C1518),
            Color(0xFFF2B8B5),
            Icons.Default.Stop
        )
        TypingState.Completed -> Triple(
            Color(0xFF0D3B66),
            Color(0xFF64B5F6),
            Icons.Default.CheckCircle
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = state.name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                letterSpacing = 0.5.sp
            )
        }
    }
}
