/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import android.annotation.SuppressLint
import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class DisplayService : CarAppService() {
    /**
     * Only allow Android Auto since it makes no sense to run this on an Android Automotive system
     * natively.
     */
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator {
        val unfilteredValidator = HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

        return HostValidator.Builder(this)
            .apply {
                for (host in unfilteredValidator.allowedHosts) {
                    if (host.key == "com.google.android.projection.gearhead") {
                        for (sig in host.value) {
                            addAllowedHost(host.key, sig)
                        }
                    }
                }
            }
            .build()
    }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen =
            DisplayScreen(carContext, this@DisplayService)
    }
}
