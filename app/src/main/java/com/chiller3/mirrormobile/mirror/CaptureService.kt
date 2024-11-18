/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.Surface
import androidx.annotation.MainThread
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.chiller3.mirrormobile.Notifications
import com.chiller3.mirrormobile.Preferences
import com.chiller3.mirrormobile.R

class CaptureService : Service() {
    companion object {
        private val TAG = CaptureService::class.java.simpleName

        private val ACTION_START = "${CaptureService::class.java.canonicalName}.start"
        private val ACTION_CANCEL = "${CaptureService::class.java.canonicalName}.cancel"

        private const val EXTRA_RESULT = "result"
        private const val EXTRA_DATA = "data"

        fun createStartIntent(context: Context, requestResult: Int, requestData: Intent) =
            Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT, requestResult)
                putExtra(EXTRA_DATA, requestData)
            }

        fun createCancelIntent(context: Context) =
            Intent(context, CaptureService::class.java).apply {
                action = ACTION_CANCEL
            }
    }

    private val binder = CaptureBinder()
    private lateinit var notifications: Notifications
    private var pendingCancel: Boolean = false
    private var wakeLock: WakeLock? = null
    private var projection: MediaProjection? = null
    private var projectionListener: Listener? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        notifications = Notifications(this)
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Received intent: $intent")

        when (intent?.action) {
            ACTION_START -> {
                if (projection != null) {
                    Log.w(TAG, "Media projection is already running")
                } else {
                    val manager = getSystemService(MediaProjectionManager::class.java)
                    val requestResult = intent.getIntExtra(EXTRA_RESULT, -1)
                    val requestData = IntentCompat.getParcelableExtra(
                        intent, EXTRA_DATA, Intent::class.java)

                    moveToForeground()

                    val prefs = Preferences(this)

                    if (prefs.wakeLock) {
                        val pm = getSystemService(PowerManager::class.java)
                        @Suppress("DEPRECATION")
                        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG)
                        wakeLock!!.acquire()
                    }

                    Log.i(TAG, "Starting media projection: $requestResult: $requestData")
                    projection = manager.getMediaProjection(requestResult, requestData!!)
                    projection!!.registerCallback(ProjectionCallback(), null)

                    notifyReadyOrCancelled()
                }
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "Media projection was cancelled")
                pendingCancel = true

                notifyReadyOrCancelled()
            }
        }

        tryStop()

        return START_NOT_STICKY
    }

    private fun tryStop() {
        if (projection == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @MainThread
    private fun moveToForeground() {
        val notification = notifications.createPersistentNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }

        ServiceCompat.startForeground(this, Notifications.ID_PERSISTENT, notification, type)
    }

    private fun notifyReadyOrCancelled() {
        projectionListener?.let {
            if (pendingCancel) {
                pendingCancel = false
                it.onCaptureCancelled()
            } else if (projection != null) {
                it.onCaptureReady()
            }
        }
    }

    inner class ProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            projectionListener?.onCaptureStopped()

            projection!!.unregisterCallback(this)
            projection = null

            virtualDisplay?.release()
            virtualDisplay = null

            wakeLock?.release()
            wakeLock = null

            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    interface Listener {
        /** Called when the capture is ready to have a surface attached. */
        fun onCaptureReady()

        /** Called when the capture is fully stopped after [CaptureBinder.stopCapture]. */
        fun onCaptureStopped()

        /** Called when the capture permission request was cancelled by the user. */
        fun onCaptureCancelled()
    }

    inner class CaptureBinder : Binder() {
        /**
         * Register for capture events.
         *
         * Only one listener can be registered at a time.
         *
         * The listener will receive [Listener.onCaptureReady] or [Listener.onCaptureCancelled]
         * now if the service was already started. Otherwise, it will receive those calls when the
         * service starts.
         *
         * The listener will also receive [Listener.onCaptureStopped] after calling [stopCapture] if
         * a capture was running as long as it does not unregister itself as a listener too soon.
         */
        @MainThread
        fun registerCaptureListener(listener: Listener) {
            if (projectionListener != null) {
                throw IllegalArgumentException(
                    "Registering duplicate listener: $projectionListener -> $listener")
            } else {
                projectionListener = listener

                // The listener may not have been registered when the service was started.
                notifyReadyOrCancelled()
            }
        }

        /**
         * Unregister capture events listener.
         *
         * [listener] must be the exact listener that was previously registered, if there was one.
         * Calling this without first calling [stopCapture] will cause an [IllegalArgumentException]
         * to be thrown because the [Surface] used by the capture would still be in use.
         */
        @MainThread
        fun unregisterCaptureListener(listener: Listener) {
            if (projectionListener != null) {
                if (projectionListener !== listener) {
                    throw IllegalArgumentException(
                        "Unregistering mismatched listener: $projectionListener != $listener")
                }

                if (virtualDisplay?.surface != null) {
                    throw IllegalArgumentException("Capture still has surface attached")
                }

                projectionListener = null
            } else {
                Log.w(TAG, "Listener not registered: $listener")
            }
        }

        /**
         * Return whether a capture session is present. If it is not, the caller will need to start
         * [CaptureRequestActivity] to request permissions before starting a capture.
         */
        @MainThread
        fun haveCaptureSession() = projection != null

        /**
         * Start capturing into the [surface].
         *
         * This must only be called after receiving [Listener.onCaptureReady] callback. It not not
         * necessary for this to be called inside the callback though. This will either start a new
         * capture or attach the surface to the existing capture if there was one.
         */
        @MainThread
        fun startCapture(width: Int, height: Int, dpi: Int, surface: Surface) {
            if (projection == null) {
                throw IllegalStateException("Projection not started yet")
            }

            if (virtualDisplay != null) {
                virtualDisplay!!.surface = surface
            } else {
                virtualDisplay = projection!!.createVirtualDisplay(
                    getString(R.string.app_name),
                    width,
                    height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    surface,
                    null,
                    null,
                )
            }
        }

        /**
         * Stop capturing or detach surface from capture.
         *
         * If [detach] is true, then the surface previously provided by [startCapture] is detached,
         * but the capture remains active. [startCapture] can be called again without re-requesting
         * permissions from the user. If [detach] is false, then the capture pipeline is shut down
         * and [CaptureRequestActivity] must be used again to request user permissions.
         */
        @MainThread
        fun stopCapture(detach: Boolean) {
            virtualDisplay?.surface = null

            if (!detach) {
                projection?.stop()
            }
        }
    }
}
