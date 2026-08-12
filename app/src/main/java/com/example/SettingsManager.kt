package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _savedText = MutableStateFlow(getSavedTextInternal())
    val savedText: StateFlow<String> = _savedText.asStateFlow()

    private val _speedMs = MutableStateFlow(getSpeedMsInternal())
    val speedMs: StateFlow<Long> = _speedMs.asStateFlow()

    private val _typoEnabled = MutableStateFlow(getTypoEnabledInternal())
    val typoEnabled: StateFlow<Boolean> = _typoEnabled.asStateFlow()

    private val _pauseEnabled = MutableStateFlow(getPauseEnabledInternal())
    val pauseEnabled: StateFlow<Boolean> = _pauseEnabled.asStateFlow()

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_SAVED_TEXT -> _savedText.value = getSavedTextInternal()
                KEY_TYPING_SPEED_MS -> _speedMs.value = getSpeedMsInternal()
                KEY_TYPO_SIMULATION -> _typoEnabled.value = getTypoEnabledInternal()
                KEY_RANDOM_PAUSES -> _pauseEnabled.value = getPauseEnabledInternal()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun getSavedTextInternal(): String {
        return prefs.getString(KEY_SAVED_TEXT, DEFAULT_TEXT) ?: DEFAULT_TEXT
    }

    private fun getSpeedMsInternal(): Long {
        return prefs.getLong(KEY_TYPING_SPEED_MS, DEFAULT_SPEED_MS)
    }

    private fun getTypoEnabledInternal(): Boolean {
        return prefs.getBoolean(KEY_TYPO_SIMULATION, DEFAULT_TYPO)
    }

    private fun getPauseEnabledInternal(): Boolean {
        return prefs.getBoolean(KEY_RANDOM_PAUSES, DEFAULT_PAUSE)
    }

    fun saveText(text: String) {
        prefs.edit().putString(KEY_SAVED_TEXT, text).apply()
        _savedText.value = text
    }

    fun clearText() {
        saveText("")
    }

    fun setSpeedMs(speedMs: Long) {
        prefs.edit().putLong(KEY_TYPING_SPEED_MS, speedMs).apply()
        _speedMs.value = speedMs
    }

    fun setTypoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TYPO_SIMULATION, enabled).apply()
        _typoEnabled.value = enabled
    }

    fun setPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RANDOM_PAUSES, enabled).apply()
        _pauseEnabled.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "autotyper_settings"
        const val KEY_SAVED_TEXT = "saved_text"
        const val KEY_TYPING_SPEED_MS = "typing_speed_ms"
        const val KEY_TYPO_SIMULATION = "typo_simulation"
        const val KEY_RANDOM_PAUSES = "random_pauses"

        const val DEFAULT_TEXT =
            "Hello! This is a test of AutoTyper Keyboard. AutoTyper types text automatically character-by-character with realistic speed variation, natural pauses, and optional typo simulation."
        const val DEFAULT_SPEED_MS = 100L
        const val DEFAULT_TYPO = true
        const val DEFAULT_PAUSE = true

        val SPEED_OPTIONS = listOf(50L, 100L, 150L, 200L, 250L, 300L)
    }
}
