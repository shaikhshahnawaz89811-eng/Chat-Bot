package com.brain.offlineai.data.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Phase 10 - real metadata lookup for a picked SAF Uri. Queries the actual
 * `OpenableColumns.DISPLAY_NAME`/`SIZE` columns the ContentResolver
 * reports for the Uri (the real, correct way to name a `content://` Uri -
 * `Uri.lastPathSegment` is not reliable for these and is only used here as
 * a last-resort fallback if the provider returns no display name at all).
 */
object UriMetadataResolver {

    fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
    }

    fun resolveMimeType(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)
}
