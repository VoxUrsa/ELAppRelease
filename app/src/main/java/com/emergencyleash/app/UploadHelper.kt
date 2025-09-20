package com.emergencyleash.app

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object UploadHelper {

    /**
     * Get the file path from a URI.
     *
     * @param context The context
     * @param uri The Uri of the selected file
     * @return The file path or null if not found
     */
    fun getPath(context: Context, uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            Log.e("UploadHelper", "Failed to get file path: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}
