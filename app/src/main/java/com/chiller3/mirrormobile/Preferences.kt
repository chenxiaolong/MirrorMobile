/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class Preferences(context: Context) {
    companion object {
        // Keep in the same order as the helper functions below.
        const val PREF_DEBUG_MODE = "debug_mode"
        private const val PREF_AUTO_START = "auto_start"
        private const val PREF_WAKE_LOCK = "wake_lock"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun registerListener(listener: OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    var isDebugMode: Boolean
        get() = prefs.getBoolean(PREF_DEBUG_MODE, false)
        set(enabled) = prefs.edit { putBoolean(PREF_DEBUG_MODE, enabled) }

    var autoStart: Boolean
        get() = prefs.getBoolean(PREF_AUTO_START, true)
        set(enabled) = prefs.edit { putBoolean(PREF_AUTO_START, enabled) }

    var wakeLock: Boolean
        get() = prefs.getBoolean(PREF_WAKE_LOCK, true)
        set(enabled) = prefs.edit { putBoolean(PREF_WAKE_LOCK, enabled) }
}
