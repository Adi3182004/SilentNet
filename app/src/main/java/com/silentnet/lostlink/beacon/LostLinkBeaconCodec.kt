package com.silentnet.lostlink.beacon

import android.util.Log
import java.nio.ByteBuffer
import java.util.UUID

/**
 * LostLinkBeaconCodec
 * 
 * Responsibility:
 * - Encode LostLinkBeaconPacket to ByteArray for compact transmission.
 * - Decode ByteArray to LostLinkBeaconPacket.
 * 
 * New Ultra-Compact Binary Format (V4 - 15 bytes):
 * [0]     - Protocol Version (1 byte, value = 4)
 * [1]     - Rotation Version (1 byte)
 * [2-9]   - Beacon ID Hash (8 bytes - MSB of UUID)
 * [10-13] - Created At (4 bytes - Unix seconds)
 * [14]    - TTL (1 byte - Minutes until expiration)
 */
object LostLinkBeaconCodec {
    private const val TAG = "LostLinkBeaconCodec"
    private const val BINARY_PV = 4

    /**
     * Encodes packet to a 15-byte binary structure.
     */
    fun encode(packet: LostLinkBeaconPacket): ByteArray {
        return try {
            val buffer = ByteBuffer.allocate(15)
            buffer.put(BINARY_PV.toByte())
            buffer.put(packet.rotationVersion.toByte())
            
            // Extract MSB from UUID string
            val uuid = UUID.fromString(packet.beaconId)
            buffer.putLong(uuid.mostSignificantBits)
            
            // Convert millis to seconds for 4-byte storage
            val caSec = (packet.createdAt / 1000).toInt()
            buffer.putInt(caSec)
            
            // Calculate TTL in minutes (max 255 mins)
            val ttlMs = packet.expiresAt - packet.createdAt
            val ttlMin = (ttlMs / (1000 * 60)).coerceIn(0, 255).toInt()
            buffer.put(ttlMin.toByte())
            
            buffer.array()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode packet: ${e.message}")
            ByteArray(0)
        }
    }

    /**
     * Decodes 15-byte binary structure back to packet.
     */
    fun decode(bytes: ByteArray): LostLinkBeaconPacket? {
        return try {
            if (bytes.size < 15) {
                Log.w(TAG, "Packet too small: ${bytes.size} bytes")
                return null
            }
            
            val buffer = ByteBuffer.wrap(bytes)
            val pv = buffer.get().toInt()
            
            if (pv != BINARY_PV) {
                Log.w(TAG, "Unsupported protocol version: $pv")
                return null
            }
            
            val rv = buffer.get().toInt()
            val bidMsb = buffer.long
            val caSec = buffer.int
            val ttlMin = buffer.get().toInt() and 0xFF
            
            val createdAt = caSec.toLong() * 1000
            val expiresAt = createdAt + (ttlMin.toLong() * 60 * 1000)
            
            // Reconstruct the deterministic ID string (LSB zeroed)
            val beaconId = toCompactId(UUID(bidMsb, 0L))
            
            LostLinkBeaconPacket(
                beaconId = beaconId,
                rotationVersion = rv,
                createdAt = createdAt,
                expiresAt = expiresAt,
                protocolVersion = pv
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode packet: ${e.message}")
            null
        }
    }

    /**
     * Standardizes a UUID into the compact 8-byte identity format.
     */
    fun toCompactId(uuid: UUID): String {
        return UUID(uuid.mostSignificantBits, 0L).toString()
    }
}
