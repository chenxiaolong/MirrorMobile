/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class Preferences(context: Context) {
    companion object {
        const val CATEGORY_PERMISSIONS = "permissions"
        const val CATEGORY_DEBUG = "debug"

        // Main UI actions only
        const val PREF_MISSING_NOTIFICATIONS = "missing_notifications"
        const val PREF_MISSING_SPEED = "missing_speed"
        const val PREF_AUTO_START = "auto_start"
        const val PREF_WAKE_LOCK = "wake_lock"
        const val PREF_SAVE_LOGS = "save_logs"
        const val PREF_VERSION = "version"

        // Not associated with a UI preference
        const val PREF_DEBUG_MODE = "debug_mode"
    }

    internal val prefs = PreferenceManager.getDefaultSharedPreferences(context)

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
