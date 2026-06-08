package com.silentnet.lostlink.recovery

import com.silentnet.lostlink.data.AssetEntity
import com.silentnet.lostlink.repository.LostLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AssetTrackerManager(
    val repository: LostLinkRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun trackAsset(assetId: String, ownerId: String, name: String, type: String, linkedDeviceId: String? = null) {
        scope.launch {
            repository.saveAsset(
                AssetEntity(
                    assetId = assetId,
                    ownerId = ownerId,
                    assetName = name,
                    assetType = type,
                    linkedDeviceId = linkedDeviceId,
                    createdAt = System.currentTimeMillis(),
                    isLost = false
                )
            )
            android.util.Log.e("RUNTIME_LOG", "ASSET_REGISTERED assetId=$assetId name=$name")
        }
    }

    /**
     * Phase 14 & 15: Trusted Device Registration and Auto Asset Creation
     */
    fun registerTrustedDevice(
        deviceId: String,
        userId: String,
        username: String,
        deviceName: String,
        deviceType: String,
        publicKey: String
    ) {
        scope.launch {
            // 1. Store in TrustedDevice table
            val device = com.silentnet.lostlink.data.TrustedDeviceEntity(
                deviceId = deviceId,
                userId = userId,
                username = username,
                deviceName = deviceName,
                deviceType = deviceType,
                publicKey = publicKey
            )
            repository.saveTrustedDevice(device)
            android.util.Log.e("RUNTIME_LOG", "TRUSTED_DEVICE_REGISTERED deviceId=$deviceId username=$username")

            // 2. Automatically create Asset
            val assetId = "PHONE_$deviceId"
            val asset = AssetEntity(
                assetId = assetId,
                ownerId = userId,
                assetName = "$username Phone",
                assetType = "Phone",
                linkedDeviceId = deviceId,
                isLost = false
            )
            repository.saveAsset(asset)
            android.util.Log.e("RUNTIME_LOG", "ASSET_REGISTERED assetId=$assetId type=Phone linkedDeviceId=$deviceId")
        }
    }

    fun setAssetLost(assetId: String, isLost: Boolean) {
        scope.launch {
            repository.updateAssetLostStatus(assetId, isLost)
            if (isLost) {
                val caseId = "CASE_${System.currentTimeMillis()}"
                val lostCase = com.silentnet.lostlink.data.LostCaseEntity(
                    caseId = caseId,
                    assetId = assetId,
                    status = "ACTIVE"
                )
                repository.saveLostCase(lostCase)
                android.util.Log.e("RUNTIME_LOG", "LOST_CASE_CREATED caseId=$caseId assetId=$assetId")
            } else {
                val activeCase = repository.getActiveCaseForAsset(assetId)
                if (activeCase != null) {
                    repository.updateLostCaseStatus(activeCase.caseId, "FOUND", System.currentTimeMillis())
                    android.util.Log.e("RUNTIME_LOG", "DEVICE_MARKED_FOUND assetId=$assetId")
                    android.util.Log.e("RUNTIME_LOG", "LOST_CASE_CLOSED caseId=${activeCase.caseId}")
                }
            }
        }
    }

    fun getAssets(ownerId: String) = repository.getAssetsByOwner(ownerId)
    
    fun getLostAssets() = repository.getLostAssets()
}
