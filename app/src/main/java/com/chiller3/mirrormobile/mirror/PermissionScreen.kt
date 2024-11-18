/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.mirror

import androidx.car.app.CarContext
import androidx.car.app.OnRequestPermissionsListener
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import com.chiller3.mirrormobile.Permissions
import com.chiller3.mirrormobile.R

class PermissionScreen(carContext: CarContext) : Screen(carContext), OnClickListener,
    OnRequestPermissionsListener {
    private var waitingForRequest = false

    override fun onGetTemplate(): Template {
        val message = if (waitingForRequest) {
            carContext.getString(R.string.permission_message_check_phone)
        } else {
            carContext.getString(R.string.permission_message_rationale)
        }

        val template = MessageTemplate.Builder(message)
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )

        if (!waitingForRequest) {
            template.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.permission_button_grant))
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener(ParkedOnlyOnClickListener.create(this@PermissionScreen))
                    .build()
            )
        }

        return template.build()
    }

    override fun onClick() {
        carContext.requestPermissions(Permissions.ALL.asList(), this)
        waitingForRequest = true
        invalidate()
    }

    override fun onRequestPermissionsResult(
        grantedPermissions: MutableList<String>,
        rejectedPermissions: MutableList<String>,
    ) {
        if (rejectedPermissions.isEmpty()) {
            finish()
        } else {
            waitingForRequest = false
            invalidate()
        }
    }
}
