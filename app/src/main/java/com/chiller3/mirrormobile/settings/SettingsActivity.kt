/*
 * SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.settings

import com.chiller3.mirrormobile.PreferenceBaseActivity
import com.chiller3.mirrormobile.PreferenceBaseFragment

class SettingsActivity : PreferenceBaseActivity() {
    override val actionBarTitle: CharSequence? = null

    override val showUpButton: Boolean = false

    override fun createFragment(): PreferenceBaseFragment = SettingsFragment()
}
