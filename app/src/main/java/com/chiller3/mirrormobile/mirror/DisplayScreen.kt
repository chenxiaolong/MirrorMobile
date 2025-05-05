/*
 * SPDX-FileCopyrightText: 2024-2025 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.HostException
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.chiller3.mirrormobile.Permissions
import com.chiller3.mirrormobile.Preferences
import com.chiller3.mirrormobile.R

class DisplayScreen(carContext: CarContext, private val activityLaunchContext: Context) :
    Screen(carContext), DefaultLifecycleObserver,
    SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection, CaptureService.Listener,
    SurfaceCallback, OnCarDataAvailableListener<Speed> {
    companion object {
        private val TAG = DisplayScreen::class.java.simpleName
    }

    private val prefs = Preferences(carContext)
    private val appManager = carContext.getCarService(AppManager::class.java)
    private val carInfo = carContext.getCarService(CarHardwareManager::class.java).carInfo
    private var state: DisplayState = DisplayState.DrivingInitial
        set(s) {
            Log.d(TAG, "Updating state: $field -> $s")
            field = s
            invalidate()
        }

    init {
        lifecycle.addObserver(this)
        prefs.prefs.registerOnSharedPreferenceChangeListener(this)
    }

    @ExperimentalCarApi
    override fun onGetTemplate(): Template {
        val (titleResId, listener, enabled) = when (state) {
            // Navigation template must have an action button.
            DisplayState.ParkedInitial,
            DisplayState.DrivingInitial,
            is DisplayState.ParkedHaveService,
            is DisplayState.DrivingHaveService,
            is DisplayState.ParkedHaveSurface,
            is DisplayState.DrivingHaveSurface,
            is DisplayState.CancelledHaveService,
            is DisplayState.Driving,
            is DisplayState.Requesting ->
                Triple(R.string.mirror_bottom_unavailable, {}, false)
            is DisplayState.Cancelled,
            is DisplayState.Inactive ->
                Triple(R.string.mirror_button_start, ::onStartPressed, true)
            is DisplayState.Mirroring ->
                Triple(R.string.mirror_button_stop, ::onStopPressed, true)
        }

        val action = Action.Builder()
            .setTitle(carContext.getString(titleResId))
            .setOnClickListener(listener)
            .setEnabled(enabled)
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(action)
            .apply {
                // With Android Auto, exiting is really restarting the app, so only show the button
                // when debug mode is enabled to avoid confusion.
                if (Preferences(carContext).isDebugMode) {
                    addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(R.string.mirror_button_exit))
                            .setOnClickListener(carContext::finishCarApp)
                            .build()
                    )
                }
            }
            .build()

        val message = when (state) {
            // Don't show any message while loading or while mirroring.
            DisplayState.ParkedInitial,
            DisplayState.DrivingInitial,
            is DisplayState.ParkedHaveService,
            is DisplayState.DrivingHaveService,
            is DisplayState.ParkedHaveSurface,
            is DisplayState.DrivingHaveSurface,
            is DisplayState.Mirroring ->
                null
            is DisplayState.CancelledHaveService,
            is DisplayState.Cancelled ->
                carContext.getString(R.string.mirror_state_cancelled)
            is DisplayState.Driving ->
                carContext.getString(R.string.mirror_state_driving)
            is DisplayState.Inactive ->
                carContext.getString(R.string.mirror_state_inactive)
            is DisplayState.Requesting ->
                carContext.getString(R.string.mirror_state_requesting)
        }

        return if (message != null) {
            MapWithContentTemplate.Builder()
                .setActionStrip(actionStrip)
                .setContentTemplate(
                    MessageTemplate.Builder(message)
                        .build()
                )
                .build()
        } else {
            NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build()
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        Log.d(TAG, "onCreate()")

        appManager.setSurfaceCallback(this)

        val intent = Intent(carContext, CaptureService::class.java)
        carContext.bindService(intent, this, Context.BIND_AUTO_CREATE)

        if (Permissions.have(carContext, Permissions.ALL)) {
            registerSpeedListener()
        } else {
            screenManager.pushForResult(PermissionScreen(carContext)) {
                registerSpeedListener()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy()")

        unregisterSpeedListener()

        state.getTransitionOrNull(DisplayState.StopMirroring::class.java)
            ?.stopMirroring()
            ?.let { state = it }

        onBinderGone()
        carContext.unbindService(this)

        appManager.setSurfaceCallback(null)

        prefs.prefs.unregisterOnSharedPreferenceChangeListener(this)

        lifecycle.removeObserver(this)
    }

    private fun registerSpeedListener() {
        try {
            carInfo.addSpeedListener(carContext.mainExecutor, this)
        } catch (e: HostException) {
            // When Android Auto is disconnected from the host, this can fail even though the screen
            // hasn't been destroyed yet.
            Log.w(TAG, "Failed to register speed listener", e)
        }
    }

    private fun unregisterSpeedListener() {
        try {
            carInfo.removeSpeedListener(this)
        } catch (e: HostException) {
            // This can also fail if Android Auto already disconnected from the host.
            Log.w(TAG, "Failed to unregister speed listener", e)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Preferences.PREF_DEBUG_MODE) {
            invalidate()
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        Log.d(TAG, "Connected to service: $name")

        val captureBinder = service as CaptureService.CaptureBinder

        state = state.getTransition(DisplayState.AttachService::class.java)
            .attachService(captureBinder, this, carContext, prefs.autoStart)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Log.d(TAG, "Disconnected from service (crash?): $name")
        onBinderGone()
    }

    private fun onBinderGone() {
        state = state.getTransition(DisplayState.DetachService::class.java).detachService(this)
    }

    override fun onCaptureReady() {
        Log.d(TAG, "Capture is ready")

        state.getTransitionOrNull(DisplayState.StartMirroring::class.java)
            ?.tryStartMirroring(activityLaunchContext, prefs.autoStart)
            ?.let { state = it }
    }

    override fun onCaptureStopped() {
        Log.d(TAG, "Capture was stopped")

        // This is fallible because we could potentially receive this in any state where the service
        // is connected, but only some states require a transition.
        state.getTransitionOrNull(DisplayState.CaptureStopped::class.java)
            ?.captureStopped()
            ?.let { state = it }
    }

    override fun onCaptureCancelled() {
        Log.d(TAG, "Capture was cancelled")

        state = state.getTransition(DisplayState.RequestCancelled::class.java).requestCancelled()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface is available")

        // This is fallible because Android Auto sometimes calls this twice without first calling
        // onSurfaceDestroyed(). We don't allow no-op attachSurface() state transitions.
        state.getTransitionOrNull(DisplayState.AttachSurface::class.java)
            ?.attachSurface(surfaceContainer, carContext, prefs.autoStart)
            ?.let { state = it }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Surface is being destroyed")

        state = state.getTransition(DisplayState.DetachSurface::class.java).detachSurface()
    }

    override fun onCarDataAvailable(data: Speed) {
        Log.d(TAG, "Speed: $data")

        val isDriving = if (data.displaySpeedMetersPerSecond.status == CarValue.STATUS_SUCCESS) {
            data.displaySpeedMetersPerSecond.value!! >= 0.001f
        } else {
            Log.w(TAG, "Speed not available: $data")
            // Assume that we're driving if we don't know.
            true
        }

        val newState = if (isDriving) {
            state.getTransitionOrNull(DisplayState.StartDriving::class.java)?.startDriving()
        } else {
            // We intentionally don't respect the autostart preference here. If the user stopped the
            // mirroring, it would be very annoying to have the prompt appear every time the vehicle
            // comes to a stop.
            state.getTransitionOrNull(DisplayState.StopDriving::class.java)
                ?.stopDriving(carContext, false)
        }

        // We only transition on state changes.
        if (newState != null) {
            state = newState
        }
    }

    private fun onStartPressed() {
        Log.d(TAG, "Start button pressed")

        state = state.getTransition(DisplayState.StartMirroring::class.java)
            .tryStartMirroring(activityLaunchContext, true)
    }

    private fun onStopPressed() {
        Log.d(TAG, "Stop button pressed")

        state = state.getTransition(DisplayState.StopMirroring::class.java).stopMirroring()
    }
}
