package com.example

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class TypingState {
    Idle,
    Typing,
    Paused,
    Stopped,
    Completed
}

class TypingEngine {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var typingJob: Job? = null

    private val _state = MutableStateFlow(TypingState.Idle)
    val state: StateFlow<TypingState> = _state.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _totalChars = MutableStateFlow(0)
    val totalChars: StateFlow<Int> = _totalChars.asStateFlow()

    private val _progressPercentage = MutableStateFlow(0f)
    val progressPercentage: StateFlow<Float> = _progressPercentage.asStateFlow()

    private val _statusText = MutableStateFlow("Idle")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var textToType: String = ""
    private var inputConnection: InputConnection? = null

    fun startTyping(
        connection: InputConnection?,
        text: String,
        speedMs: Long,
        typoEnabled: Boolean,
        pauseEnabled: Boolean
    ) {
        if (text.isEmpty()) {
            _statusText.value = "No text saved"
            return
        }

        stopTyping()

        inputConnection = connection
        textToType = text
        _totalChars.value = text.length
        _currentIndex.value = 0
        _progressPercentage.value = 0f
        _state.value = TypingState.Typing
        _statusText.value = "Typing (0/${text.length})"

        typingJob = scope.launch {
            var index = 0
            while (index < textToType.length) {
                // Check if stopped
                if (_state.value == TypingState.Stopped) {
                    break
                }

                // Check if paused
                while (_state.value == TypingState.Paused) {
                    _statusText.value = "Paused ($index/${textToType.length})"
                    delay(100)
                    if (_state.value == TypingState.Stopped) break
                }

                if (_state.value == TypingState.Stopped) {
                    break
                }

                val conn = inputConnection
                if (conn == null) {
                    _statusText.value = "No input field connected"
                    _state.value = TypingState.Stopped
                    break
                }

                val targetChar = textToType[index]

                // Typo Simulation (3% chance on letters)
                val isLetter = targetChar.isLetter()
                val shouldMakeTypo = typoEnabled && isLetter && Random.nextFloat() < 0.03f

                if (shouldMakeTypo) {
                    val wrongChar = getNearbyChar(targetChar)
                    _statusText.value = "Simulating typo..."
                    conn.commitText(wrongChar.toString(), 1)
                    delay(Random.nextLong(100, 250))
                    conn.deleteSurroundingText(1, 0)
                    delay(Random.nextLong(80, 180))
                }

                // Commit correct character
                conn.commitText(targetChar.toString(), 1)

                index++
                _currentIndex.value = index
                val progress = (index.toFloat() / textToType.length)
                _progressPercentage.value = progress
                _statusText.value = "Typing ($index/${textToType.length})"

                if (index >= textToType.length) {
                    _state.value = TypingState.Completed
                    _statusText.value = "Completed ($index/$index)"
                    break
                }

                // Random Pauses (5% chance, 500ms - 1000ms delay)
                val shouldPause = pauseEnabled && Random.nextFloat() < 0.05f
                if (shouldPause && index < textToType.length - 1) {
                    val pauseMs = Random.nextLong(500, 1000)
                    _statusText.value = "Pausing briefly..."
                    delay(pauseMs)
                }

                // Varied Speed (±20%)
                val variance = (Random.nextDouble() * 0.4) - 0.2 // -0.2 to +0.2
                val delayMs = (speedMs * (1.0 + variance)).toLong().coerceAtLeast(10L)
                delay(delayMs)
            }
        }
    }

    fun pauseTyping() {
        if (_state.value == TypingState.Typing) {
            _state.value = TypingState.Paused
        }
    }

    fun resumeTyping() {
        if (_state.value == TypingState.Paused) {
            _state.value = TypingState.Typing
        }
    }

    fun stopTyping() {
        typingJob?.cancel()
        typingJob = null
        if (_state.value != TypingState.Idle && _state.value != TypingState.Completed) {
            _state.value = TypingState.Stopped
            _statusText.value = "Stopped"
        }
    }

    private fun getNearbyChar(c: Char): Char {
        val qwerty = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
        )
        val lowerC = c.lowercaseChar()
        for (row in qwerty) {
            val idx = row.indexOf(lowerC)
            if (idx != -1) {
                val offset = if (idx > 0 && Random.nextBoolean()) -1 else 1
                val newIdx = (idx + offset).coerceIn(0, row.length - 1)
                val typoChar = row[newIdx]
                return if (c.isUpperCase()) typoChar.uppercaseChar() else typoChar
            }
        }
        return if (c == 'a') 's' else 'a'
    }
}
