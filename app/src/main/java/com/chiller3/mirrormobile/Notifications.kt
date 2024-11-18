/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class Notifications(private val context: Context) {
    companion object {
        private const val CHANNEL_ID_PERSISTENT = "persistent"

        private val LEGACY_CHANNEL_IDS = arrayOf<String>()

        const val ID_PERSISTENT = 1
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** Create a low priority notification channel for the background services notification. */
    private fun createPersistentChannel() = NotificationChannel(
        CHANNEL_ID_PERSISTENT,
        context.getString(R.string.notification_channel_persistent_name),
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = context.getString(R.string.notification_channel_persistent_desc)
    }

    /**
     * Ensure notification channels are up-to-date.
     *
     * Legacy notification channels are deleted without migrating settings.
     */
    fun updateChannels() {
        notificationManager.createNotificationChannels(listOf(
            createPersistentChannel(),
        ))
        LEGACY_CHANNEL_IDS.forEach { notificationManager.deleteNotificationChannel(it) }
    }

    fun createPersistentNotification(): Notification {
        return Notification.Builder(context, CHANNEL_ID_PERSISTENT).run {
            setContentTitle(context.getString(R.string.notification_mirroring_active))
            setSmallIcon(R.drawable.ic_notifications)
            setOngoing(true)
            setOnlyAlertOnce(true)

            // Inhibit 10-second delay when showing persistent notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }

            build()
        }
    }
}
