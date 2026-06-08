package com.silentnet.util

import android.util.Log
import com.silentnet.data.FileFragmentEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class FileFragmentationManager {
    private val FRAGMENT_SIZE = 64 * 1024 // 64KB fragments for mesh efficiency

    fun splitFile(file: File, fileId: String, targetId: String, isGroup: Boolean): List<FileFragmentEntity> {
        val fragments = mutableListOf<FileFragmentEntity>()
        val totalFragments = ((file.length() + FRAGMENT_SIZE - 1) / FRAGMENT_SIZE).toInt()
        val buffer = ByteArray(FRAGMENT_SIZE)
        
        try {
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                var index = 0
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val data = if (bytesRead == FRAGMENT_SIZE) buffer.copyOf() else buffer.copyOfRange(0, bytesRead)
                    fragments.add(
                        FileFragmentEntity(
                            fileId = fileId,
                            fragmentIndex = index,
                            totalFragments = totalFragments,
                            data = data,
                            checksum = calculateChecksum(data),
                            targetId = targetId,
                            isGroup = isGroup
                        )
                    )
                    index++
                }
            }
        } catch (e: Exception) {
            Log.e("FileFragmentation", "Error splitting file", e)
        }
        return fragments
    }

    fun reassembleFile(fragments: List<FileFragmentEntity>, targetFile: File): Boolean {
        if (fragments.isEmpty()) return false
        val sorted = fragments.sortedBy { it.fragmentIndex }
        
        val totalExpected = sorted[0].totalFragments
        if (sorted.size != totalExpected) {
            Log.e("FileFragmentation", "Reassembly failed: missing fragments (${sorted.size}/$totalExpected)")
            return false
        }

        try {
            FileOutputStream(targetFile).use { fos ->
                for (fragment in sorted) {
                    if (calculateChecksum(fragment.data) != fragment.checksum) {
                        Log.e("FileFragmentation", "Checksum mismatch at index ${fragment.fragmentIndex}")
                        return false
                    }
                    fos.write(fragment.data)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("FileFragmentation", "Reassembly error", e)
            return false
        }
    }

    private fun calculateChecksum(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun calculateFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
