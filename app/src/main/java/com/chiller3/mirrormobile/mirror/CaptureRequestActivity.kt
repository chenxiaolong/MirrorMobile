/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CaptureRequestActivity : ComponentActivity() {
    private val requestProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startForegroundService(
                    CaptureService.createStartIntent(this, it.resultCode, it.data!!),
                )
            } else {
                // Starting a media projection foreground service after the request is denied is
                // forbidden and will trigger a SecurityException. We still want to start the
                // service though so that its listener can be made aware of the cancellation.
                startService(CaptureService.createCancelIntent(this))
            }

            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(MediaProjectionManager::class.java)
        requestProjection.launch(manager.createScreenCaptureIntent())
    }
}
