/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.chiller3.mirrormobile.BuildConfig
import com.chiller3.mirrormobile.Logcat
import com.chiller3.mirrormobile.Permissions
import com.chiller3.mirrormobile.PreferenceBaseFragment
import com.chiller3.mirrormobile.Preferences
import com.chiller3.mirrormobile.R
import com.chiller3.mirrormobile.extension.formattedString
import com.chiller3.mirrormobile.view.LongClickablePreference
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceBaseFragment(), Preference.OnPreferenceClickListener,
    LongClickablePreference.OnPreferenceLongClickListener {
    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var prefs: Preferences
    private lateinit var categoryPermissions: PreferenceCategory
    private lateinit var categoryDebug: PreferenceCategory
    private lateinit var prefMissingNotifications: Preference
    private lateinit var prefMissingSpeed: Preference
    private lateinit var prefVersion: LongClickablePreference
    private lateinit var prefSaveLogs: Preference

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.all { it.value }) {
                refreshPermissions()
            } else {
                startActivity(Permissions.getAppInfoIntent(requireContext()))
            }
        }
    private val requestSafSaveLogs =
        registerForActivityResult(ActivityResultContracts.CreateDocument(Logcat.MIMETYPE)) { uri ->
            uri?.let {
                viewModel.saveLogs(it)
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_root, rootKey)

        val context = requireContext()

        prefs = Preferences(context)

        categoryPermissions = findPreference(Preferences.CATEGORY_PERMISSIONS)!!
        categoryDebug = findPreference(Preferences.CATEGORY_DEBUG)!!

        prefMissingNotifications = findPreference(Preferences.PREF_MISSING_NOTIFICATIONS)!!
        prefMissingNotifications.onPreferenceClickListener = this

        prefMissingSpeed = findPreference(Preferences.PREF_MISSING_SPEED)!!
        prefMissingSpeed.onPreferenceClickListener = this

        prefVersion = findPreference(Preferences.PREF_VERSION)!!
        prefVersion.onPreferenceClickListener = this
        prefVersion.onPreferenceLongClickListener = this

        prefSaveLogs = findPreference(Preferences.PREF_SAVE_LOGS)!!
        prefSaveLogs.onPreferenceClickListener = this

        // Call this once first to avoid UI jank from elements shifting. We call it again in
        // onResume() because allowing the permissions does not restart the activity.
        refreshPermissions()

        refreshVersion()
        refreshDebugPrefs()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alerts.collect {
                    it.firstOrNull()?.let { alert ->
                        onAlert(alert)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        refreshPermissions()
    }

    private fun refreshPermissions() {
        val context = requireContext()

        val allowedNotifications = Permissions.have(context, Permissions.NOTIFICATIONS)
        prefMissingNotifications.isVisible = !allowedNotifications

        val allowedSpeed = Permissions.have(context, Permissions.SPEED)
        prefMissingSpeed.isVisible = !allowedSpeed

        categoryPermissions.isVisible = !allowedNotifications || !allowedSpeed
    }

    private fun refreshVersion() {
        prefVersion.summary = buildString {
            append(BuildConfig.VERSION_NAME)

            append(" (")
            append(BuildConfig.BUILD_TYPE)
            if (prefs.isDebugMode) {
                append("+debugmode")
            }
            append(")")
        }
    }

    private fun refreshDebugPrefs() {
        categoryDebug.isVisible = prefs.isDebugMode
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when {
            preference === prefMissingNotifications -> {
                requestPermissions.launch(Permissions.NOTIFICATIONS)
                return true
            }
            preference === prefMissingSpeed -> {
                requestPermissions.launch(Permissions.SPEED)
                return true
            }
            preference === prefVersion -> {
                val uri = Uri.parse(BuildConfig.PROJECT_URL_AT_COMMIT)
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }
            preference === prefSaveLogs -> {
                requestSafSaveLogs.launch(Logcat.FILENAME_DEFAULT)
                return true
            }
        }

        return false
    }

    override fun onPreferenceLongClick(preference: Preference): Boolean {
        when (preference) {
            prefVersion -> {
                prefs.isDebugMode = !prefs.isDebugMode
                refreshVersion()
                refreshDebugPrefs()
                return true
            }
        }

        return false
    }

    private fun onAlert(alert: SettingsAlert) {
        val msg = when (alert) {
            is SettingsAlert.LogcatSucceeded ->
                getString(R.string.alert_logcat_success, alert.uri.formattedString)
            is SettingsAlert.LogcatFailed ->
                getString(R.string.alert_logcat_failure, alert.uri.formattedString, alert.error)
        }

        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG)
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    viewModel.acknowledgeFirstAlert()
                }
            })
            .show()
    }
}
