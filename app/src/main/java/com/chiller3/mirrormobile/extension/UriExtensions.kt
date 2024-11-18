/*
 * SPDX-FileCopyrightText: 2023 Andrew Gunnerson
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.chiller3.mirrormobile.extension

import android.content.ContentResolver
import android.net.Uri

val Uri.formattedString: String
    get() = when (scheme) {
        ContentResolver.SCHEME_FILE -> path!!
        ContentResolver.SCHEME_CONTENT -> {
            val prefix = when (authority) {
                "com.android.externalstorage.documents" -> ""
                // Include the authority to reduce ambiguity when this isn't a SAF URI provided by
                // Android's local filesystem document provider
                else -> "[$authority] "
            }
            val segments = pathSegments

            // If this looks like a SAF tree/document URI, then try and show the document ID. This
            // cannot be implemented in a way that prevents all false positives.
            if (segments.size == 4 && segments[0] == "tree" && segments[2] == "document") {
                prefix + segments[3]
            } else if (segments.size == 2 && (segments[0] == "tree" || segments[0] == "document")) {
                prefix + segments[1]
            } else {
                toString()
            }
        }
        else -> toString()
    }