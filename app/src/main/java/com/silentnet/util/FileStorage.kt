package com.silentnet.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileStorage {

    fun copyAttachment(context: Context, uri: Uri): AttachmentDraft {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(resolver, uri) ?: "attachment_${System.currentTimeMillis()}"
        val safeName = "${System.currentTimeMillis()}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        val dir = File(context.filesDir, "silentnet/attachments").apply { mkdirs() }
        val file = File(dir, safeName)

        resolver.openInputStream(uri).use { input ->
            FileOutputStream(file).use { output ->
                input?.copyTo(output)
            }
        }

        return AttachmentDraft(file.absolutePath, name, mime)
    }

    fun uriForFile(context: Context, path: String): Uri {
        val file = File(path)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun deleteAllAttachments(context: Context) {
        val dir = File(context.filesDir, "silentnet/attachments")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, null, null, null, null)
            val index = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            if (cursor != null && cursor.moveToFirst() && index >= 0) {
                cursor.getString(index)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            cursor?.close()
        }
    }
}
