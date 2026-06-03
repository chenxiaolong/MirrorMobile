/*
 * SPDX-FileCopyrightText: 2023-2026 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chiller3.mirrormobile.BuildConfig
import com.chiller3.mirrormobile.Logcat
import com.chiller3.mirrormobile.Permissions
import com.chiller3.mirrormobile.Preferences
import com.chiller3.mirrormobile.R
import com.chiller3.mirrormobile.extension.formattedString
import com.chiller3.mirrormobile.ui.AppScreen
import com.chiller3.mirrormobile.ui.BetterSegmentedShapes
import com.chiller3.mirrormobile.ui.Preference
import com.chiller3.mirrormobile.ui.PreferenceCategory
import com.chiller3.mirrormobile.ui.PreferenceColumn
import com.chiller3.mirrormobile.ui.SwitchPreference
import com.chiller3.mirrormobile.ui.betterSegmentedShapes
import com.chiller3.mirrormobile.ui.theme.AppTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val prefs = remember { Preferences(context) }
    var reloadPrefs by remember { mutableIntStateOf(0) }
    val autoStart = remember(reloadPrefs) { prefs.autoStart }
    val wakeLock = remember(reloadPrefs) { prefs.wakeLock }
    val isDebugMode = remember(reloadPrefs) { prefs.isDebugMode }

    var reloadPerms by remember { mutableIntStateOf(0) }
    val notificationsGranted = remember(reloadPerms) {
        Permissions.have(context, Permissions.NOTIFICATIONS)
    }
    val speedGranted = remember(reloadPerms) { Permissions.have(context, Permissions.SPEED) }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.all { it.value }) {
            reloadPrefs++
        } else {
            context.startActivity(Permissions.getAppInfoIntent(context))
        }
    }
    val requestSafSaveLogs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Logcat.MIMETYPE),
    ) { uri ->
        uri?.let { viewModel.saveLogs(it) }
    }

    AppScreen(
        title = { Text(text = stringResource(R.string.app_name)) },
    ) { params ->
        LaunchedEffect(Unit) {
            viewModel.alerts.collect { alerts ->
                val alert = alerts.firstOrNull() ?: return@collect
                val msg = when (alert) {
                    is SettingsAlert.LogcatSucceeded -> resources.getString(
                        R.string.alert_logcat_success,
                        alert.uri.formattedString,
                    )
                    is SettingsAlert.LogcatFailed -> resources.getString(
                        R.string.alert_logcat_failure,
                        alert.uri.formattedString,
                        alert.error,
                    )
                    SettingsAlert.BrowserNotFound ->
                        resources.getString(R.string.alert_browser_not_found)
                    SettingsAlert.DocumentsUINotFound ->
                        resources.getString(R.string.alert_documentsui_not_found)
                }

                params.snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                viewModel.acknowledgeFirstAlert()
            }
        }

        SettingsContent(
            notificationsGranted = notificationsGranted,
            speedGranted = speedGranted,
            autoStart = autoStart,
            wakeLock = wakeLock,
            isDebugMode = isDebugMode,
            onNotificationsGrant = {
                requestPermissions.launch(Permissions.NOTIFICATIONS)
            },
            onSpeedGrant = {
                requestPermissions.launch(Permissions.SPEED)
            },
            onAutoStartChange = { enabled ->
                prefs.autoStart = enabled
                reloadPrefs++
            },
            onWakeLockChange = { enabled ->
                prefs.wakeLock = enabled
                reloadPrefs++
            },
            onDebugModeChange = { enabled ->
                prefs.isDebugMode = enabled
                reloadPrefs++
            },
            onSourceRepoOpen = {
                val uri = BuildConfig.PROJECT_URL_AT_COMMIT.toUri()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                    viewModel.addAlert(SettingsAlert.BrowserNotFound)
                }
            },
            onSaveLogs = {
                requestSafSaveLogs.launch(Logcat.FILENAME_DEFAULT)
            },
            contentPadding = params.contentPadding,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsContent(
    notificationsGranted: Boolean,
    speedGranted: Boolean,
    autoStart: Boolean,
    wakeLock: Boolean,
    isDebugMode: Boolean,
    onNotificationsGrant: () -> Unit,
    onSpeedGrant: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onWakeLockChange: (Boolean) -> Unit,
    onDebugModeChange: (Boolean) -> Unit,
    onSourceRepoOpen: () -> Unit,
    onSaveLogs: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    data class MissingPermission(
        val key: String,
        val title: String,
        val summary: String,
        val onGrant: () -> Unit,
    )

    val missingPermissions = mutableListOf<MissingPermission>().apply {
        if (!notificationsGranted) {
            add(MissingPermission(
                key = "missing_notifications",
                title = stringResource(R.string.pref_missing_notifications_name),
                summary = stringResource(R.string.pref_missing_notifications_desc),
                onGrant = onNotificationsGrant,
            ))
        }
        if (!speedGranted) {
            add(MissingPermission(
                key = "missing_speed",
                title = stringResource(R.string.pref_missing_speed_name),
                summary = stringResource(R.string.pref_missing_speed_desc),
                onGrant = onSpeedGrant,
            ))
        }
    }

    PreferenceColumn(contentPadding = contentPadding) {
        if (missingPermissions.isNotEmpty()) {
            item(key = "permissions") {
                PreferenceCategory(
                    title = { Text(text = stringResource(R.string.pref_header_permissions)) },
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(missingPermissions, key = { _, m -> m.key }) { index, missing ->
                Preference(
                    onClick = missing.onGrant,
                    shapes = betterSegmentedShapes(index, missingPermissions.size),
                    title = { Text(text = missing.title) },
                    summary = { Text(text = missing.summary) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(key = "behavior") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_behavior)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "auto_start") {
            SwitchPreference(
                checked = autoStart,
                onCheckedChange = onAutoStartChange,
                shapes = BetterSegmentedShapes.top(),
                title = { Text(text = stringResource(R.string.pref_auto_start_name)) },
                summary = { Text(text = stringResource(R.string.pref_auto_start_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "wake_lock") {
            SwitchPreference(
                checked = wakeLock,
                onCheckedChange = onWakeLockChange,
                shapes = BetterSegmentedShapes.bottom(),
                title = { Text(text = stringResource(R.string.pref_wake_lock_name)) },
                summary = { Text(text = stringResource(R.string.pref_wake_lock_desc)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "about") {
            PreferenceCategory(
                title = { Text(text = stringResource(R.string.pref_header_about)) },
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "version") {
            Preference(
                onClick = onSourceRepoOpen,
                onLongClick = { onDebugModeChange(!isDebugMode) },
                shapes = BetterSegmentedShapes.single(),
                title = { Text(text = stringResource(R.string.pref_version_name)) },
                summary = { Text(text = versionSummary(isDebugMode)) },
                modifier = Modifier.animateItem(),
            )
        }

        if (isDebugMode) {
            item(key = "debug") {
                PreferenceCategory(
                    title = { Text(text = stringResource(R.string.pref_header_debug)) },
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "save_logs") {
                Preference(
                    onClick = onSaveLogs,
                    shapes = BetterSegmentedShapes.single(),
                    title = { Text(text = stringResource(R.string.pref_save_logs_name)) },
                    summary = { Text(text = stringResource(R.string.pref_save_logs_desc)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun versionSummary(isDebugMode: Boolean): String {
    val suffix = if (isDebugMode) "+debugmode" else ""

    return "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}${suffix})"
}

@Preview(
    name = "Light Mode",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun PreviewSettingsScreen() {
    AppTheme {
        AppScreen(
            title = { Text(text = stringResource(R.string.app_name)) },
        ) { params ->
            SettingsContent(
                notificationsGranted = false,
                speedGranted = false,
                autoStart = true,
                wakeLock = true,
                isDebugMode = true,
                onNotificationsGrant = {},
                onSpeedGrant = {},
                onAutoStartChange = {},
                onWakeLockChange = {},
                onDebugModeChange = {},
                onSourceRepoOpen = {},
                onSaveLogs = {},
                contentPadding = params.contentPadding,
            )
        }
    }
}
