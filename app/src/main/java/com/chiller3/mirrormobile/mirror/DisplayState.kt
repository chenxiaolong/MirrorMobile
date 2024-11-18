/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer

/**
 * This implements a state machine for [DisplayScreen] to clearly express what the valid states are,
 * since there are many different lifecycles in play.
 */
sealed interface DisplayState {
    companion object {
        private val TAG = DisplayState::class.java.simpleName
    }

    sealed interface HaveServiceState {
        val captureBinder: CaptureService.CaptureBinder
    }

    sealed interface HaveSurfaceState {
        val surfaceContainer: SurfaceContainer
    }

    fun<T : Transition> getTransitionOrNull(clazz: Class<T>): T? {
        return if (clazz.isInstance(this)) {
            @Suppress("UNCHECKED_CAST")
            this as T
        } else {
            null
        }
    }

    fun<T : Transition> getTransition(clazz: Class<T>): T {
        return getTransitionOrNull(clazz)
            ?: throw IllegalStateException("Cannot use ${clazz.simpleName} on: $this")
    }

    sealed interface Transition

    sealed interface AttachService : Transition {
        fun toAttachedService(captureBinder: CaptureService.CaptureBinder): DisplayState

        fun attachService(
            captureBinder: CaptureService.CaptureBinder,
            listener: CaptureService.Listener,
            carContext: CarContext,
            canRequest: Boolean,
        ): DisplayState {
            captureBinder.registerCaptureListener(listener)

            val newState = toAttachedService(captureBinder)

            return if (newState is StartMirroring) {
                newState.tryStartMirroring(carContext, canRequest)
            } else {
                newState
            }
        }
    }

    sealed interface DetachService : Transition, HaveServiceState {
        fun toDetachedService(): DisplayState

        fun detachService(listener: CaptureService.Listener): DisplayState {
            try {
                captureBinder.unregisterCaptureListener(listener)
            } catch (e: Exception) {
                // The service may have crashed.
                Log.e(TAG, "Failed to unregister capture listener", e)
            }

            return toDetachedService()
        }
    }

    sealed interface AttachSurface : Transition {
        fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState

        fun attachSurface(
            surfaceContainer: SurfaceContainer,
            carContext: CarContext,
            canRequest: Boolean,
        ): DisplayState {
            val newState = toAttachedSurface(surfaceContainer)

            return if (newState is StartMirroring) {
                newState.tryStartMirroring(carContext, canRequest)
            } else {
                newState
            }
        }
    }

    sealed interface DetachSurface : Transition {
        fun toDetachedSurface(): DisplayState

        fun detachSurface(): DisplayState {
            Log.d(TAG, "Detaching capture")

            // We're likely just invisible. If the screen is closed, onDestroy() will take care of
            // shutting down the capture session.
            if (this is HaveServiceState) {
                captureBinder.stopCapture(true)
            }

            return toDetachedSurface()
        }
    }

    sealed interface RequestCancelled : Transition {
        fun toRequestCancelled(): DisplayState

        // No side effect.
        fun requestCancelled(): DisplayState = toRequestCancelled()
    }

    sealed interface StartDriving : Transition {
        fun toStartedDriving(): DisplayState

        // No side effect.
        fun startDriving(): DisplayState = toStartedDriving()
    }

    sealed interface StopDriving : Transition {
        fun toStoppedDriving(): DisplayState

        // No side effect.
        fun stopDriving(carContext: CarContext, canRequest: Boolean): DisplayState {
            val newState = toStoppedDriving()

            return if (newState is StartMirroring) {
                newState.tryStartMirroring(carContext, canRequest)
            } else {
                newState
            }
        }
    }

    sealed interface StartMirroring : Transition, HaveServiceState, HaveSurfaceState {
        fun toStartedMirroring(): DisplayState

        fun toStartedRequest(): DisplayState

        fun tryStartMirroring(carContext: CarContext, canRequest: Boolean): DisplayState {
            if (captureBinder.haveCaptureSession()) {
                Log.d(TAG, "Attaching to capture session")

                captureBinder.startCapture(
                    surfaceContainer.width,
                    surfaceContainer.height,
                    surfaceContainer.dpi,
                    surfaceContainer.surface!!,
                )

                return toStartedMirroring()
            } else if (canRequest) {
                Log.d(TAG, "Starting capture permission request")

                carContext.startActivity(
                    Intent(carContext, CaptureRequestActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )

                return toStartedRequest()
            } else {
                return this as DisplayState
            }
        }
    }

    sealed interface StopMirroring : Transition, HaveServiceState {
        fun toStoppedMirroring(): DisplayState

        fun stopMirroring(): DisplayState {
            Log.d(TAG, "Stopping capture")

            captureBinder.stopCapture(false)
            // The state will be updated in DisplayScreen.onCaptureStopped().

            return toStoppedMirroring()
        }
    }

    sealed interface CaptureStopped : Transition {
        fun toCaptureStopped(): DisplayState

        // No side effect.
        fun captureStopped(): DisplayState = toCaptureStopped()
    }

    data object ParkedInitial : DisplayState, AttachService, AttachSurface, StartDriving {
        override fun toAttachedService(captureBinder: CaptureService.CaptureBinder): DisplayState =
            ParkedHaveService(captureBinder)

        override fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState =
            ParkedHaveSurface(surfaceContainer)

        override fun toStartedDriving(): DisplayState = DrivingInitial
    }

    data object DrivingInitial : DisplayState, AttachService, AttachSurface, StopDriving {
        override fun toAttachedService(captureBinder: CaptureService.CaptureBinder): DisplayState =
            DrivingHaveService(captureBinder)

        override fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState =
            DrivingHaveSurface(surfaceContainer)

        override fun toStoppedDriving(): DisplayState = ParkedInitial
    }

    data class ParkedHaveService(
        override val captureBinder: CaptureService.CaptureBinder,
    ) : DisplayState, HaveServiceState, DetachService, AttachSurface, RequestCancelled,
        StartDriving, StopMirroring {
        override fun toDetachedService(): DisplayState = ParkedInitial

        override fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState =
            Inactive(captureBinder, surfaceContainer)

        override fun toRequestCancelled(): DisplayState = CancelledHaveService(captureBinder)

        override fun toStartedDriving(): DisplayState = DrivingHaveService(captureBinder)

        // For when the screen is destroyed after the surface is detached.
        override fun toStoppedMirroring(): DisplayState = this
    }

    data class DrivingHaveService(
        override val captureBinder: CaptureService.CaptureBinder,
    ) : DisplayState, HaveServiceState, DetachService, AttachSurface, RequestCancelled,
        StopDriving, StopMirroring {
        override fun toDetachedService(): DisplayState = DrivingInitial

        override fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState =
            Driving(captureBinder, surfaceContainer)

        // We ignore this and avoid bothering the user.
        override fun toRequestCancelled(): DisplayState = this

        override fun toStoppedDriving(): DisplayState = ParkedHaveService(captureBinder)

        // For when the screen is destroyed after the surface is detached.
        override fun toStoppedMirroring(): DisplayState = this
    }

    data class ParkedHaveSurface(
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveSurfaceState, AttachService, DetachSurface, StartDriving {
        override fun toAttachedService(captureBinder: CaptureService.CaptureBinder): DisplayState =
            Inactive(captureBinder, surfaceContainer)

        override fun toDetachedSurface(): DisplayState = ParkedInitial

        override fun toStartedDriving(): DisplayState = DrivingHaveSurface(surfaceContainer)
    }

    data class DrivingHaveSurface(
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveSurfaceState, AttachService, DetachSurface, StopDriving {
        override fun toAttachedService(captureBinder: CaptureService.CaptureBinder): DisplayState =
            Driving(captureBinder, surfaceContainer)

        override fun toDetachedSurface(): DisplayState = DrivingInitial

        override fun toStoppedDriving(): DisplayState = ParkedHaveSurface(surfaceContainer)
    }

    data class CancelledHaveService(
        override val captureBinder: CaptureService.CaptureBinder,
    ) : DisplayState, HaveServiceState, DetachService, AttachSurface, StartDriving {
        override fun toDetachedService(): DisplayState = ParkedInitial

        override fun toAttachedSurface(surfaceContainer: SurfaceContainer): DisplayState =
            Cancelled(captureBinder, surfaceContainer)

        override fun toStartedDriving(): DisplayState = DrivingHaveService(captureBinder)
    }

    data class Cancelled(
        override val captureBinder: CaptureService.CaptureBinder,
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveServiceState, HaveSurfaceState, DetachService, DetachSurface,
        StartDriving, StartMirroring {
        override fun toDetachedService(): DisplayState = ParkedHaveSurface(surfaceContainer)

        override fun toDetachedSurface(): DisplayState = ParkedHaveService(captureBinder)

        override fun toStartedDriving(): DisplayState = Driving(captureBinder, surfaceContainer)

        override fun toStartedMirroring(): DisplayState = Mirroring(captureBinder, surfaceContainer)

        override fun toStartedRequest(): DisplayState = Requesting(captureBinder, surfaceContainer)
    }

    data class Driving(
        override val captureBinder: CaptureService.CaptureBinder,
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveServiceState, HaveSurfaceState, DetachService, DetachSurface,
        RequestCancelled, StopDriving, StopMirroring {
        override fun toDetachedService(): DisplayState = DrivingHaveSurface(surfaceContainer)

        override fun toDetachedSurface(): DisplayState = DrivingHaveService(captureBinder)

        // We can hit this if the user starts driving after cancelling the request but before the
        // CaptureService has processed it.
        override fun toRequestCancelled(): DisplayState = this

        override fun toStoppedDriving(): DisplayState = Inactive(captureBinder, surfaceContainer)

        // For when the screen is destroyed after the surface is detached.
        override fun toStoppedMirroring(): DisplayState = this
    }

    data class Inactive(
        override val captureBinder: CaptureService.CaptureBinder,
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveServiceState, HaveSurfaceState, DetachService, DetachSurface,
        StartDriving, StartMirroring {
        override fun toDetachedService(): DisplayState = ParkedHaveSurface(surfaceContainer)

        override fun toDetachedSurface(): DisplayState = ParkedHaveService(captureBinder)

        override fun toStartedDriving(): DisplayState = Driving(captureBinder, surfaceContainer)

        override fun toStartedMirroring(): DisplayState = Mirroring(captureBinder, surfaceContainer)

        override fun toStartedRequest(): DisplayState = Requesting(captureBinder, surfaceContainer)
    }

    data class Requesting(
        override val captureBinder: CaptureService.CaptureBinder,
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveServiceState, HaveSurfaceState, DetachService, DetachSurface,
        RequestCancelled, StartDriving, StartMirroring, CaptureStopped {
        override fun toDetachedService(): DisplayState = ParkedHaveSurface(surfaceContainer)

        override fun toDetachedSurface(): DisplayState = ParkedHaveService(captureBinder)

        override fun toRequestCancelled(): DisplayState = Cancelled(captureBinder, surfaceContainer)

        override fun toStartedDriving(): DisplayState = Driving(captureBinder, surfaceContainer)

        override fun toStartedMirroring(): DisplayState = Mirroring(captureBinder, surfaceContainer)

        override fun toStartedRequest(): DisplayState = this

        override fun toCaptureStopped(): DisplayState = Inactive(captureBinder, surfaceContainer)
    }

    data class Mirroring(
        override val captureBinder: CaptureService.CaptureBinder,
        override val surfaceContainer: SurfaceContainer,
    ) : DisplayState, HaveServiceState, HaveSurfaceState, DetachService, DetachSurface,
        StartDriving, StopMirroring, CaptureStopped {
        override fun toDetachedService(): DisplayState = ParkedHaveSurface(surfaceContainer)

        override fun toDetachedSurface(): DisplayState = ParkedHaveService(captureBinder)

        override fun toStartedDriving(): DisplayState = Driving(captureBinder, surfaceContainer)

        override fun toStoppedMirroring(): DisplayState = Inactive(captureBinder, surfaceContainer)

        override fun toCaptureStopped(): DisplayState = Inactive(captureBinder, surfaceContainer)
    }
}
