package com.silentnet.transport

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.silentnet.data.*
import com.silentnet.emergency.EmergencyAlertManager
import com.silentnet.security.SecurityManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID

data class NearbyPeer(
    val endpointId: String,
    val username: String,
    val fullName: String = "",
    val nickname: String? = null,
    val nodeId: String? = null,
    val publicKey: String? = null
)

data class MeshRoute(
    val targetNodeId: String,
    val nextHopNodeId: String,
    var nextHopEndpointId: String,
    var hopCount: Int,
    var successCount: Int = 0,
    var failureCount: Int = 0,
    var lastUsed: Long = 0,
    var lastSeen: Long = System.currentTimeMillis(),
    var averageLatency: Long = 0,
    var deliveryConfidence: Double = 1.0,
    var timestamp: Long = System.currentTimeMillis(),
    var ttl: Int = 5,
    var expiration: Long = System.currentTimeMillis() + 3600000,
    var relayContribution: Int = 0,
    var reputationMultiplier: Double = 1.0
) {
    fun getScore(): Double {
        val confidence = (successCount + 1).toDouble() / (successCount + failureCount + 1)
        val freshness = 1.0 / (1.0 + (System.currentTimeMillis() - lastSeen) / 60000.0)
        val latencyFactor = if (averageLatency > 0) 1.0 / (1.0 + averageLatency / 1000.0) else 0.5
        val loadPenalty = 1.0 / (1.0 + relayContribution / 100.0)
        return (confidence * 0.3 + freshness * 0.2 + latencyFactor * 0.1 + reputationMultiplier * 0.3 + loadPenalty * 0.1) / hopCount
    }
}

class TransportManager(
    private val context: Context,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val groupRepository: com.silentnet.data.GroupRepository,
    private val identityManager: com.silentnet.security.IdentityManager,
    private val meshPacketDao: com.silentnet.data.MeshPacketDao,
    private val recoveryDao: com.silentnet.data.RecoveryDao,
    private val analyticsDao: com.silentnet.data.AnalyticsDao,
    private val trustedAdminDao: com.silentnet.data.TrustedAdminDao,
    private val reputationDao: com.silentnet.data.ReputationDao,
    private val researchDao: com.silentnet.data.ResearchDao,
    private val disasterDao: com.silentnet.data.DisasterDao,
    private val walkieDao: com.silentnet.data.WalkieDao,
    private val voiceNoteRepository: com.silentnet.data.VoiceNoteRepository,
    private val fileFragmentDao: com.silentnet.data.FileFragmentDao,
    private val fileFragmentationManager: com.silentnet.util.FileFragmentationManager
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val SERVICE_ID = "com.silentnet.OFFLINE_MESSENGER"
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val PRIORITY_CRITICAL = 0
    private val PRIORITY_MEDIUM = 1
    private val PRIORITY_LOW = 2

    private data class QueuedPayload(
        val endpointId: String,
        val payload: Payload,
        val priority: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : Comparable<QueuedPayload> {
        override fun compareTo(other: QueuedPayload): Int {
            if (this.priority != other.priority) return this.priority.compareTo(other.priority)
            return this.timestamp.compareTo(other.timestamp)
        }
    }

    private val outboundQueue = java.util.concurrent.PriorityBlockingQueue<QueuedPayload>()

    private fun enqueuePayload(endpointId: String, payload: Payload, priority: Int) {
        outboundQueue.put(QueuedPayload(endpointId, payload, priority))
    }

    private var isAnonymousMode: Boolean = false
    
    fun setAnonymousMode(enabled: Boolean) {
        this.isAnonymousMode = enabled
        Log.d("SilentNetRecovery", if (enabled) "Anonymous Mode Enabled" else "Anonymous Mode Disabled")
    }

    fun getPeerStatus(username: String): String {
        val nodeId = usernameToNodeId[username] ?: username
        val isNearby = _peers.value.any { it.username == username || it.nodeId == nodeId }
        if (isNearby) return "Nearby"
        
        synchronized(routingTable) {
            val routes = routingTable[nodeId]?.values ?: return "Offline"
            val now = System.currentTimeMillis()
            val fresh = routes.any { now - it.lastSeen < 600000 } // 10 minutes
            return if (fresh) "Mesh" else "Offline"
        }
    }

    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    private var myUsername: String? = null
    private var myFullName: String? = null
    private var myNodeId: String? = null
    private var myPublicKey: String? = null

    private val _networkEvents = MutableSharedFlow<NetworkEventEntity>(replay = 0)
    val networkEvents: SharedFlow<NetworkEventEntity> = _networkEvents.asSharedFlow()

    private fun logEvent(type: String, details: String) {
        scope.launch {
            val event = NetworkEventEntity(type = type, details = details)
            _networkEvents.emit(event)
            analyticsDao.insertEvent(event)
        }
    }

    private val activeConnections = mutableMapOf<String, String>()
    private val pendingConnections = mutableMapOf<String, String>()
    private val connectingEndpoints = Collections.synchronizedSet(mutableSetOf<String>())
    private val nodeIdToUsername = mutableMapOf<String, String>()
    private val usernameToNodeId = mutableMapOf<String, String>()
    private val peerPublicKeys = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val incomingPayloads = Collections.synchronizedMap(mutableMapOf<Long, Payload>())
    private val incomingFilePayloadIds = Collections.synchronizedMap(mutableMapOf<Long, Long>())
    private val incomingWalkieFilePayloadIds = Collections.synchronizedMap(mutableMapOf<Long, String>())

    private val _emergencyEvents = MutableSharedFlow<MessageEntity>(replay = 0)
    val emergencyEvents: SharedFlow<MessageEntity> = _emergencyEvents.asSharedFlow()

    private var emergencyAlertManager: EmergencyAlertManager? = null

    fun setEmergencyAlertManager(manager: EmergencyAlertManager) {
        this.emergencyAlertManager = manager
    }

    private val routingTable = Collections.synchronizedMap(mutableMapOf<String, MutableMap<String, MeshRoute>>())
    private val processedPackets = Collections.synchronizedMap(mutableMapOf<String, Long>())
    private var isStarted = false
    private var reliabilityJob: Job? = null
    
    interface MeshRouter {
        fun determineNextHops(targetNodeId: String, isEmergency: Boolean = false): List<String>
        fun updateRoute(targetNodeId: String, nextHopEndpointId: String, hopCount: Int)
        fun invalidateRoute(endpointId: String)
        fun recordSuccess(targetNodeId: String, endpointId: String, rtt: Long = 0)
        fun recordFailure(targetNodeId: String, endpointId: String)
        fun recordRelayContribution(targetNodeId: String, endpointId: String)
        fun getPredictionScore(targetNodeId: String): Double
    }

    private val meshRouter = object : MeshRouter {
        override fun determineNextHops(targetNodeId: String, isEmergency: Boolean): List<String> {
            synchronized(routingTable) {
                val routes = routingTable[targetNodeId]?.values?.toList() ?: emptyList()
                val neighbors = activeConnections.keys.toList()
                if (isEmergency) return neighbors
                if (routes.isEmpty()) {
                    if (neighbors.size <= 2) return neighbors
                    val gossipCount = (neighbors.size * 0.6).toInt().coerceAtLeast(2)
                    return neighbors.shuffled().take(gossipCount)
                }
                val rankedRoutes = routes.filter { activeConnections.containsKey(it.nextHopEndpointId) }.sortedByDescending { it.getScore() }
                if (rankedRoutes.isEmpty()) return neighbors.shuffled().take((neighbors.size * 0.4).toInt().coerceAtLeast(1))
                return rankedRoutes.map { it.nextHopEndpointId }
            }
        }

        override fun updateRoute(targetNodeId: String, nextHopEndpointId: String, hopCount: Int) {
            if (targetNodeId == myNodeId) return
            val nextHopNodeId = activeConnections[nextHopEndpointId] ?: return
            synchronized(routingTable) {
                val routes = routingTable.getOrPut(targetNodeId) { mutableMapOf() }
                val existing = routes[nextHopNodeId]
                if (existing == null) {
                    val newRoute = MeshRoute(targetNodeId = targetNodeId, nextHopNodeId = nextHopNodeId, nextHopEndpointId = nextHopEndpointId, hopCount = hopCount)
                    scope.launch { newRoute.reputationMultiplier = reputationDao.getReputation(nextHopNodeId)?.getReputationMultiplier() ?: 1.0 }
                    routes[nextHopNodeId] = newRoute
                } else {
                    existing.lastSeen = System.currentTimeMillis()
                    existing.nextHopEndpointId = nextHopEndpointId
                    if (hopCount < existing.hopCount) existing.hopCount = hopCount
                }
            }
        }

        override fun invalidateRoute(endpointId: String) {
            synchronized(routingTable) {
                routingTable.values.forEach { innerMap -> innerMap.values.removeIf { it.nextHopEndpointId == endpointId } }
                routingTable.entries.removeIf { it.value.isEmpty() }
            }
        }

        override fun recordSuccess(targetNodeId: String, endpointId: String, rtt: Long) {
            val nextHopNodeId = activeConnections[endpointId] ?: return
            synchronized(routingTable) {
                val route = routingTable[targetNodeId]?.get(nextHopNodeId) ?: return
                route.successCount++
                route.lastUsed = System.currentTimeMillis()
                route.lastSeen = System.currentTimeMillis()
                if (rtt > 0) route.averageLatency = if (route.averageLatency == 0L) rtt else (route.averageLatency * 0.7 + rtt * 0.3).toLong()
                route.deliveryConfidence = (route.successCount + 1).toDouble() / (route.successCount + route.failureCount + 1)
                scope.launch { reputationDao.recordSuccess(nextHopNodeId) }
            }
        }

        override fun recordFailure(targetNodeId: String, endpointId: String) {
            val nextHopNodeId = activeConnections[endpointId] ?: return
            synchronized(routingTable) {
                val route = routingTable[targetNodeId]?.get(nextHopNodeId) ?: return
                route.failureCount++
                route.deliveryConfidence = (route.successCount + 1).toDouble() / (route.successCount + route.failureCount + 1)
                scope.launch { reputationDao.recordFailure(nextHopNodeId) }
            }
        }

        override fun recordRelayContribution(targetNodeId: String, endpointId: String) {
            val nextHopNodeId = activeConnections[endpointId] ?: return
            synchronized(routingTable) {
                val route = routingTable[targetNodeId]?.get(nextHopNodeId) ?: return
                route.relayContribution++
                scope.launch { reputationDao.recordRelay(nextHopNodeId) }
            }
        }

        override fun getPredictionScore(targetNodeId: String): Double {
            synchronized(routingTable) {
                val routes = routingTable[targetNodeId]?.values ?: return 0.5
                val best = routes.maxByOrNull { it.getScore() } ?: return 0.0
                return best.deliveryConfidence * best.reputationMultiplier
            }
        }
    }

    init {
        scope.launch {
            while (isActive) {
                try {
                    val item = outboundQueue.take()
                    connectionsClient.sendPayload(item.endpointId, item.payload)
                } catch (e: Exception) {
                    Log.e("SilentNetTransport", "Sender error", e)
                }
            }
        }
        scope.launch {
            SecurityManager.setTrustedAdmins(trustedAdminDao.getAllAdmins().map { it.publicKeyBase64 })
            while (isActive) {
                delay(300000)
                val now = System.currentTimeMillis()
                synchronized(processedPackets) { processedPackets.entries.removeIf { it.value < now } }
                synchronized(routingTable) {
                    routingTable.values.forEach { innerMap -> innerMap.entries.removeIf { now - it.value.timestamp > 3600000 } }
                    routingTable.entries.removeIf { it.value.isEmpty() }
                }
                meshPacketDao.deleteExpired(now)
                cleanupTempFiles()
            }
        }
    }
    fun getRoutingTable(): Map<String, MutableMap<String, MeshRoute>> {
        return routingTable
    }

    fun getActiveConnections(): Map<String, String> {
        return activeConnections
    }
    fun start(username: String, fullName: String, nickname: String?, nodeId: String) {
        if (isStarted) return
        isStarted = true
        
        myUsername = username; myFullName = fullName; myNodeId = nodeId; myPublicKey = identityManager.getPublicKeyBase64()
        val json = JSONObject().apply { put("u", username); put("id", nodeId) }.toString()
        Log.d("SilentNetTransport", "ADVERTISING_STARTED with payload: $json")
        connectionsClient.startAdvertising(json, SERVICE_ID, connectionLifecycleCallback, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
        Log.d("SilentNetTransport", "DISCOVERY_STARTED")
        
        startReliabilityLoop()
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        
        connectionsClient.stopAdvertising(); connectionsClient.stopDiscovery(); connectionsClient.stopAllEndpoints()
        activeConnections.clear(); pendingConnections.clear(); nodeIdToUsername.clear(); usernameToNodeId.clear()
        reliabilityJob?.cancel()
    }

    private fun startReliabilityLoop() {
        reliabilityJob?.cancel()
        reliabilityJob = scope.launch {
            while (isActive) {
                try {
                    if (activeConnections.isNotEmpty()) {
                        val pendingMessages = messageRepository.getPendingMessages()
                        for (msg in pendingMessages) {
                            val contact = contactRepository.findById(msg.contactId)
                            if (contact != null) {
                                // Preserve packet identity by using a stable pId based on message ID
                                val pId = "MSG_${msg.id}"
                                sendMessage(contact.contactUsername, msg, pId)
                            }
                        }
                    }
                    
                    // ACK Timeout: move Transmitting (1) back to Pending (0) after 45s
                    messageRepository.resetTransmittingMessages(45000)
                } catch (e: Exception) {
                    Log.e("SilentNetTransport", "Reliability loop error", e)
                }
                delay(15000)
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            try {
                Log.d("SilentNetTransport", "ENDPOINT_FOUND: $endpointId (${info.endpointName})")
                val json = JSONObject(info.endpointName)
                val peerUsername = json.getString("u"); val peerNodeId = json.optString("id", peerUsername)
                nodeIdToUsername[peerNodeId] = peerUsername
                usernameToNodeId[peerUsername] = peerNodeId
                
                val peer = NearbyPeer(endpointId = endpointId, username = peerUsername, fullName = peerUsername, nodeId = peerNodeId)
                if (peer.nodeId != myNodeId && myNodeId != null && peer.nodeId != null) {
                    _peers.value = (_peers.value.filter { it.nodeId != peer.nodeId } + peer)
                    
                    val isAlreadyConnected = activeConnections.values.contains(peerNodeId)
                    val isAlreadyPending = pendingConnections.values.contains(peerNodeId)
                    val isConnecting = connectingEndpoints.contains(endpointId)

                    if (!isAlreadyConnected && !isAlreadyPending && !isConnecting) {
                        // Deterministic initiator: only the side with the lexicographically smaller nodeId initiates
                        if (myNodeId!! < peerNodeId) {
                            Log.d("SilentNetTransport", "Initiating connection to $peerUsername ($endpointId)")
                            connectingEndpoints.add(endpointId)
                            val myInfo = JSONObject().apply { put("u", myUsername); put("id", myNodeId) }.toString()
                            connectionsClient.requestConnection(myInfo, endpointId, connectionLifecycleCallback)
                                .addOnFailureListener { e ->
                                    Log.e("SilentNetTransport", "Request connection failed for $endpointId: ${e.message}")
                                    connectingEndpoints.remove(endpointId)
                                }
                        } else {
                            Log.d("SilentNetTransport", "Waiting for $peerUsername ($endpointId) to initiate (myId > peerId)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SilentNetTransport", "Discovery parse failed", e)
            }
        }
        override fun onEndpointLost(endpointId: String) {
            _peers.value = _peers.value.filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("SilentNetTransport", "CONNECTION_INITIATED: $endpointId (${info.endpointName})")
            try {
                val json = JSONObject(info.endpointName)
                val peerNodeId = json.getString("id")
                pendingConnections[endpointId] = peerNodeId
            } catch (e: Exception) {
                pendingConnections[endpointId] = info.endpointName
            }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                Log.d("SilentNetTransport", "CONNECTION_ACCEPTED: $endpointId")
                val nodeId = pendingConnections.remove(endpointId) ?: endpointId
                activeConnections[endpointId] = nodeId
                sendIdentity(endpointId)

                // Flush stored packets for the newly connected peer
                scope.launch {
                    val storedPackets = meshPacketDao.getPacketsForTarget(nodeId)
                    storedPackets.forEach { packet ->
                        enqueuePayload(endpointId, Payload.fromBytes(packet.encryptedPacketJson.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM)
                        meshPacketDao.deleteByPacketId(packet.packetId)
                    }
                }
            } else {
                Log.d("SilentNetTransport", "CONNECTION_FAILED: $endpointId - ${result.status.statusMessage}")
                pendingConnections.remove(endpointId)
            }
        }
        override fun onDisconnected(endpointId: String) {
            val nodeId = activeConnections.remove(endpointId)
            pendingConnections.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            meshRouter.invalidateRoute(endpointId)
            nodeId?.let { onPeerDisconnected?.invoke(it) }
        }
    }

    private fun sendIdentity(endpointId: String) {
        scope.launch {
            try {
                val identityJson = JSONObject().apply {
                    put("u", myUsername)
                    put("f", myFullName)
                    put("n", "")
                    put("id", myNodeId)
                    put("spk", myPublicKey)
                }.toString()
                
                val packet = wrapInMeshPacket(activeConnections[endpointId] ?: "PEER", "identity_exchange", identityJson)
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)))
                Log.d("SilentNetTransport", "IDENTITY_SENT to $endpointId")
            } catch (e: Exception) {
                Log.e("SilentNetTransport", "Identity send failed", e)
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            incomingPayloads[payload.id] = payload
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { handleIncomingData(endpointId, String(it, StandardCharsets.UTF_8)) }
                Payload.Type.STREAM -> payload.asStream()?.asInputStream()?.let { onStreamReceived?.invoke(endpointId, it) }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = incomingPayloads.remove(update.payloadId)
                if (payload?.type == Payload.Type.FILE) { /* File handling ... */ }
            }
        }
    }

    fun sendStream(targetUsername: String, inputStream: java.io.InputStream) {
        val targetNodeId = usernameToNodeId[targetUsername] ?: targetUsername
        val endpointId = activeConnections.entries.find { it.value == targetNodeId }?.key
        if (endpointId != null) {
            connectionsClient.sendPayload(endpointId, Payload.fromStream(inputStream))
        } else {
            // If not direct peer, we can't send stream yet (mesh stream not supported)
            Log.e("SilentNetTransport", "Cannot send stream to non-direct peer $targetUsername")
        }
    }

    var callSignalingListener: ((fromUsername: String, fromFullName: String, type: String, payload: String) -> Unit)? = null
    var onStreamReceived: ((endpointId: String, stream: java.io.InputStream) -> Unit)? = null
    var onPeerDisconnected: ((nodeId: String) -> Unit)? = null

    private fun handleIncomingData(endpointId: String, data: String) {
        scope.launch {
            try {
                val meshPacket = JSONObject(data)
                val packetId = meshPacket.getString("pId"); val targetNodeId = meshPacket.getString("tId")
                val sourceNodeId = meshPacket.getString("sId"); val ttl = meshPacket.getInt("ttl")
                val hopCount = meshPacket.getInt("hC"); val payload = meshPacket.getString("pay")
                val payloadType = meshPacket.optString("pTy", "msg")

                if (payloadType == "call_request" || payloadType == "call_accept" || payloadType == "call_decline" || payloadType == "call_end") {
                    val fromU = nodeIdToUsername[sourceNodeId] ?: sourceNodeId

                    val contact = contactRepository.findOrCreateContact(
                        myUsername ?: "",
                        fromU,
                        fromU
                    )

                    callSignalingListener?.invoke(
                        fromU,
                        contact.alias,
                        payloadType,
                        payload
                    )

                    return@launch
                }

                if (payloadType == "identity_exchange") {
                    val identity = JSONObject(payload)
                    val peerUsername = identity.getString("u")
                    val peerFullName = identity.getString("f")
                    val peerNodeId = identity.getString("id")
                    val peerPk = identity.optString("spk")
                    
                    Log.d("SilentNetTransport", "IDENTITY_RECEIVED from $peerNodeId ($peerUsername)")
                    
                    nodeIdToUsername[peerNodeId] = peerUsername
                    usernameToNodeId[peerUsername] = peerNodeId
                    if (peerPk.isNotEmpty()) peerPublicKeys[peerNodeId] = peerPk
                    
                    val updatedPeer = NearbyPeer(
                        endpointId = endpointId,
                        username = peerUsername,
                        fullName = peerFullName,
                        nodeId = peerNodeId,
                        publicKey = peerPk.takeIf { it.isNotEmpty() }
                    )
                    
                    _peers.value = (_peers.value.filter { it.nodeId != peerNodeId } + updatedPeer)
                    
                    // Persistence moved to explicit Add Chat action to keep discovery clean
                    // contactRepository.findOrCreateContact(...)
                    Log.d("SilentNetTransport", "PEER_DISCOVERED: $peerUsername (not persisted)")
                    return@launch
                }

                if (processedPackets.containsKey(packetId)) return@launch
                synchronized(processedPackets) { processedPackets[packetId] = System.currentTimeMillis() + 10 * 60 * 1000 }
                meshRouter.updateRoute(sourceNodeId, endpointId, hopCount)

                val group = groupRepository.findGroupById(targetNodeId)
                if (group != null) {
                    if (!group.isJoined) return@launch
                    if (payloadType == "group_msg") {
                        val keyId = meshPacket.optString("kId")
                        val groupKeyEntity = groupRepository.findKey(targetNodeId, keyId)
                        val myPrivKey = identityManager.getPrivateKey()
                        if (groupKeyEntity != null && myPrivKey != null) {
                            val decryptedKeyBase64 = com.silentnet.security.CryptographyManager.decryptPayload(groupKeyEntity.encryptedKey, myPrivKey)
                            if (decryptedKeyBase64 != null) {
                                val keyBytes = android.util.Base64.decode(decryptedKeyBase64, android.util.Base64.DEFAULT)
                                val decryptedPayload = com.silentnet.security.CryptographyManager.decryptWithGroupKey(payload, keyBytes)
                                if (decryptedPayload != null) processLocalGroupPayload(targetNodeId, sourceNodeId, decryptedPayload)
                            }
                        }
                    } else if (payloadType == "group_file_frag") {
                        handleIncomingFragment(targetNodeId, sourceNodeId, payload, true)
                    } else if (payloadType == "group_key_update") {
                        handleGroupKeyUpdate(payload)
                    } else if (payloadType == "recovery_post") {
                        val postJson = JSONObject(payload)
                        recoveryDao.insertPost(RecoveryPostEntity(postId = postJson.getString("id"), category = postJson.getString("cat"), content = postJson.getString("txt"), authorAlias = postJson.getString("aut"), authorNodeId = postJson.getString("aid"), timestamp = postJson.getLong("ts"), priority = postJson.getInt("pri"), isAnonymous = postJson.getBoolean("anon"), expiration = postJson.getLong("ts") + 86400000))
                    } else if (payloadType == "recovery_group") {
                        val grpJson = JSONObject(payload)
                        recoveryDao.insertGroup(RecoveryGroupEntity(groupNodeId = grpJson.getString("id"), name = grpJson.getString("nm"), description = grpJson.optString("ds"), creatorNodeId = grpJson.getString("cid"), createdAt = grpJson.getLong("ts")))
                    } else if (payloadType == "group_leave") {
                        val leaveData = JSONObject(payload)
                        groupRepository.removeMember(leaveData.getString("gId"), leaveData.getString("id"))
                    } else if (payloadType == "voice_note_meta") {
                        val meta = JSONObject(payload)
                        voiceNoteRepository.insert(VoiceNoteEntity(voiceNoteId = meta.getString("vnId"), sender = meta.getString("s"), recipient = meta.optString("r").takeIf { it.isNotEmpty() }, groupId = meta.optString("g").takeIf { it.isNotEmpty() }, duration = meta.getLong("dur"), size = meta.getLong("size"), filePath = "", deliveryState = 0))
                    }
                    if (ttl > 1) relayPacket(meshPacket, endpointId)
                    return@launch
                }

                if (targetNodeId == "BROADCAST" || targetNodeId == "LOSTLINK_SERVICE" || targetNodeId == "WALKIE_TALKIE") {
                    when (targetNodeId) {
                        "LOSTLINK_SERVICE" -> if (payloadType == "lostlink_report") {
                            val reportJson = JSONObject(payload)
                            recoveryDao.insertLostReport(LostLinkReportEntity(deviceId = reportJson.getString("deviceId"), reporterNodeId = reportJson.getString("reporter"), timestamp = reportJson.getLong("timestamp"), rssi = reportJson.getInt("rssi"), confidence = reportJson.getDouble("conf"), nodeInfo = reportJson.optString("info")))
                        }
                        "WALKIE_TALKIE" -> when (payloadType) {
                            "walkie_segment" -> {
                                val segJson = JSONObject(payload)
                                walkieDao.insertSegment(WalkieSegmentEntity(segmentId = packetId, channelId = segJson.getString("channelId"), senderNodeId = sourceNodeId, senderAlias = segJson.getString("alias"), filePath = "", timestamp = segJson.getLong("ts"), duration = segJson.getLong("dur")))
                            }
                            "walkie_ack" -> Log.d("Walkie", "Received ack for segment ${JSONObject(payload).getString("sId")} from $sourceNodeId")
                            "walkie_presence" -> Log.d("Walkie", "Presence: ${JSONObject(payload).getString("u")} is on channel ${JSONObject(payload).getString("cId")}")
                        }
                    }
                    if (payloadType == "search_query") {
                        val searchData = JSONObject(payload)
                        handleSearchQuery(searchData.getString("qId"), sourceNodeId, searchData.getString("q"))
                    } else if (payloadType == "search_results") {
                        val resData = JSONObject(payload)
                        val qId = resData.getString("qId"); val resList = resData.getJSONArray("results")
                        for (i in 0 until resList.length()) {
                            val res = resList.getJSONObject(i)
                            recoveryDao.insertSearchResult(SearchResultEntity(queryId = qId, sourceNodeId = sourceNodeId, targetType = res.getString("type"), content = res.getString("content"), title = res.getString("title"), rankingScore = res.getDouble("score")))
                        }
                    } else if (payloadType == "voice_note_meta") {
                        val meta = JSONObject(payload)
                        voiceNoteRepository.insert(VoiceNoteEntity(voiceNoteId = meta.getString("vnId"), sender = meta.getString("s"), recipient = meta.optString("r").takeIf { it.isNotEmpty() }, groupId = meta.optString("g").takeIf { it.isNotEmpty() }, duration = meta.getLong("dur"), size = meta.getLong("size"), filePath = "", deliveryState = 0))
                    } else if (payloadType == "frag_request") {
                        val req = JSONObject(payload)
                        val fId = req.getString("fId")
                        val missing = req.getJSONArray("mis")
                        for (i in 0 until missing.length()) {
                            val idx = missing.getInt(i)
                            val fragment = fileFragmentDao.getFragment(fId, idx)
                            if (fragment != null) sendFragment(fragment)
                        }
                    }
                    else if (payloadType == "delete_msg") {
                        val delData = JSONObject(payload)
                        val remoteId = delData.getLong("lid")
                        
                        // Delete Resurrection Protection: Prune stored message from relay storage
                        meshPacketDao.deleteByRemoteId(remoteId)

                        val senderUsername = nodeIdToUsername[sourceNodeId] ?: sourceNodeId
                        val contact = contactRepository.findOrCreateContact(myUsername ?: "", senderUsername, senderUsername)
                        val msg = messageRepository.findByRemoteId(contact.id, remoteId)
                        if (msg != null && msg.groupId == targetNodeId) {
                            msg.attachmentPath?.let { path ->
                                try { java.io.File(path).delete() } catch (e: Exception) {}
                            }
                            messageRepository.markAsDeleted(msg.id)
                        }
                    } else if (payloadType == "voice_note_meta") {
                        val callReq = JSONObject(payload)
                        logEvent("Incoming Call", "From ${callReq.getString("fromF")}")
                        // Trigger UI would go here, for now we log it
                    } else if (payloadType == "call_accept") {
                        logEvent("Call Accepted", "Peer accepted the call")
                    }
                    if (ttl > 1) relayPacket(meshPacket, endpointId)
                    return@launch
                }

                if (targetNodeId == myNodeId || targetNodeId == myUsername) {
                    if (payloadType == "msg") {
                        val myPrivKey = identityManager.getPrivateKey()
                        val decrypted = if (myPrivKey != null) com.silentnet.security.CryptographyManager.decryptPayload(payload, myPrivKey) else null
                        if (decrypted != null) {
                            processLocalPayload(endpointId, decrypted)
                        } else {
                            Log.e("SilentNetTransport", "Decryption failed for message from $sourceNodeId")
                        }
                    } else if (payloadType == "group_invite") {
                        val inviteData = JSONObject(payload)
                        val gId = inviteData.getString("gId")
                        val nm = inviteData.getString("nm")
                        val ds = inviteData.getString("ds")
                        val kId = inviteData.getString("kId")
                        val eK = inviteData.getString("eK")

                        groupRepository.insertGroup(GroupEntity(groupId = gId, name = nm, description = ds, creatorNodeId = sourceNodeId, type = 0, currentKeyId = kId, isJoined = true))
                        groupRepository.insertKey(GroupKeyEntity(groupId = gId, keyId = kId, encryptedKey = eK))
                        groupRepository.insertMember(GroupMemberEntity(groupId = gId, nodeId = myNodeId ?: "Me", alias = myUsername ?: "Me", role = 0))
                        logEvent("Group Invited", "Joined group $nm via invite from $sourceNodeId")
                    } else if (payloadType == "file_frag" || payloadType == "group_file_frag") {
                        handleIncomingFragment(targetNodeId, sourceNodeId, payload, payloadType == "group_file_frag")
                    } else if (payloadType == "ack") {
                        val ackData = JSONObject(payload)
                        messageRepository.updateDeliveryStatus(ackData.getLong("lid"), 2)
                    } else if (payloadType == "delete_msg") {
                        val delData = JSONObject(payload)
                        val remoteId = delData.getLong("lid")
                        val senderUsername = nodeIdToUsername[sourceNodeId] ?: sourceNodeId
                        val contact = contactRepository.findOrCreateContact(myUsername ?: "", senderUsername, senderUsername)
                        val msg = messageRepository.findByRemoteId(contact.id, remoteId)
                        if (msg != null) {
                            msg.attachmentPath?.let { path ->
                                try { java.io.File(path).delete() } catch (e: Exception) {}
                            }
                            messageRepository.markAsDeleted(msg.id)
                        }
                    } else if (payloadType == "call_request") {
                        // Directly handle peer-to-peer call request
                        val callReq = JSONObject(payload)
                        Log.d("SilentNetCall", "Call request from ${callReq.getString("fromU")} at ${callReq.getString("ip")}:${callReq.getInt("port")}")
                    }
                } else if (ttl > 1) relayPacket(meshPacket, endpointId)
            } catch (e: Exception) {}
        }
    }

    private suspend fun handleGroupKeyUpdate(payload: String) {
        try {
            val data = JSONObject(payload)
            val groupId = data.getString("gId")
            val keyId = data.getString("kId")
            val encryptedKey = data.getString("eK")
            
            groupRepository.insertKey(GroupKeyEntity(groupId = groupId, keyId = keyId, encryptedKey = encryptedKey))
            groupRepository.updateCurrentKey(groupId, keyId)
            Log.d("SilentNetGroup", "Received group key update for $groupId")
        } catch (e: Exception) {}
    }

    fun acknowledgeEmergency(messageId: Long) {
        scope.launch {
            messageRepository.markAsAcknowledged(messageId)
        }
    }

    fun consumeViewOnce(messageId: Long) {
        scope.launch {
            messageRepository.markAsConsumed(messageId)
            messageRepository.markAsDeleted(messageId) // Also delete for privacy
        }
    }

    fun deleteForEveryone(username: String, messageId: Long) {
        scope.launch {
            messageRepository.markAsDeleted(messageId)
            val targetNodeId = usernameToNodeId[username] ?: username
            val packet = wrapInMeshPacket(targetNodeId, "delete_msg", JSONObject().apply { put("lid", messageId) }.toString())
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_CRITICAL) }
        }
    }

    fun rotateGroupKey(groupId: String) {
        scope.launch {
            val group = groupRepository.findGroupById(groupId) ?: return@launch
            val members = groupRepository.getMembers(groupId)
            val newKey = com.silentnet.security.CryptographyManager.generateGroupKey()
            val keyId = UUID.randomUUID().toString()
            
            for (member in members) {
                if (member.nodeId == myNodeId) continue
                val memberPk = peerPublicKeys[member.nodeId] ?: contactRepository.findOrCreateContact(myUsername ?: "", member.nodeId, member.alias).publicKey
                if (memberPk != null) {
                    val encryptedKey = com.silentnet.security.CryptographyManager.encryptPayload(
                        android.util.Base64.encodeToString(newKey, android.util.Base64.DEFAULT),
                        memberPk
                    )
                    if (encryptedKey != null) {
                        val payload = JSONObject().apply {
                            put("gId", groupId)
                            put("kId", keyId)
                            put("eK", encryptedKey)
                        }.toString()
                        val packet = wrapInMeshPacket(member.nodeId, "group_key_update", payload)
                        activeConnections.keys.forEach { connectionsClient.sendPayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8))) }
                    }
                }
            }
            
            val myPrivKey = identityManager.getPrivateKey()
            val myPubKey = identityManager.getPublicKeyBase64()
            if (myPrivKey != null && myPubKey != null) {
                val myEncryptedKey = com.silentnet.security.CryptographyManager.encryptPayload(
                    android.util.Base64.encodeToString(newKey, android.util.Base64.DEFAULT),
                    myPubKey
                )
                if (myEncryptedKey != null) {
                    groupRepository.insertKey(GroupKeyEntity(groupId = groupId, keyId = keyId, encryptedKey = myEncryptedKey))
                    groupRepository.updateCurrentKey(groupId, keyId)
                }
            }
        }
    }

    private fun relayPacket(meshPacket: JSONObject, incomingEndpointId: String) {
        val ttl = meshPacket.getInt("ttl")
        if (ttl <= 1) return
        
        val packetId = meshPacket.getString("pId")
        val priority = meshPacket.optInt("priority", 0)
        val targetNodeId = meshPacket.getString("tId")
        
        val updated = meshPacket.apply { 
            put("ttl", ttl - 1)
            put("hC", getInt("hC") + 1) 
        }.toString()
        
        val targets = if (priority > 0 || targetNodeId == "BROADCAST" || targetNodeId == "LOSTLINK_SERVICE") {
            activeConnections.keys.filter { it != incomingEndpointId }
        } else {
            meshRouter.determineNextHops(targetNodeId).filter { it != incomingEndpointId }
        }
        
        if (targets.isEmpty() && targetNodeId != "BROADCAST" && targetNodeId != "LOSTLINK_SERVICE" && targetNodeId != "WALKIE_TALKIE") {
            // Store and Forward: Persist packet for offline delivery
            scope.launch {
                val payloadStr = meshPacket.optString("pay")
                var msgRid: Long? = null
                try {
                    val payJson = JSONObject(payloadStr)
                    msgRid = if (payJson.has("rid")) payJson.getLong("rid") else if (payJson.has("lid")) payJson.getLong("lid") else null
                } catch (e: Exception) {}

                meshPacketDao.insert(MeshPacketEntity(
                    packetId = packetId,
                    sourceNodeId = meshPacket.getString("sId"),
                    targetNodeId = targetNodeId,
                    payloadType = meshPacket.optString("pTy", "msg"),
                    sPk = null,
                    encryptedPacketJson = updated,
                    ttl = ttl - 1,
                    hopCount = meshPacket.getInt("hC") + 1,
                    timestamp = System.currentTimeMillis(),
                    expirationTime = System.currentTimeMillis() + 86400000, // 24 hours
                    messageRemoteId = msgRid
                ))
            }
        }
        
        targets.forEach { 
            connectionsClient.sendPayload(it, Payload.fromBytes(updated.toByteArray(StandardCharsets.UTF_8)))
            meshRouter.recordRelayContribution(targetNodeId, it)
        }
    }

    private suspend fun handleIncomingFragment(targetId: String, senderId: String, payload: String, isGroup: Boolean) {
        try {
            val fragData = JSONObject(payload)
            val fId = fragData.getString("fId"); val idx = fragData.getInt("idx"); val tot = fragData.getInt("tot")
            val dataBytes = android.util.Base64.decode(fragData.getString("data"), android.util.Base64.DEFAULT)
            fileFragmentDao.insertFragment(FileFragmentEntity(fileId = fId, fragmentIndex = idx, totalFragments = tot, data = dataBytes, checksum = fragData.getString("sum"), targetId = targetId, isGroup = isGroup, senderNodeId = senderId))

            if (fileFragmentDao.getFragmentCount(fId, senderId) == tot) {
                if (fId.startsWith("WFILE_")) {
                    val segmentId = fId.removePrefix("WFILE_"); val fragments = fileFragmentDao.getFragmentsForFile(fId, senderId)
                    val dir = File(context.filesDir, "silentnet/walkie").apply { mkdirs() }
                    val encFile = File(dir, "TEMP_ENC_$fId"); fileFragmentationManager.reassembleFile(fragments, encFile)
                    val segment = walkieDao.findSegmentById(segmentId)
                    if (segment != null) {
                        val key = walkieDao.findChannelById(segment.channelId)?.channelKey; val decFile = File(dir, "$segmentId.m4a")
                        val success = if (key != null) com.silentnet.security.CryptographyManager.decryptFileWithGroupKey(encFile, decFile, key) else encFile.renameTo(decFile)
                        if (success) walkieDao.insertSegment(segment.copy(filePath = decFile.absolutePath))
                        encFile.delete()
                    }
                } else if (fId.startsWith("VNFILE_") || fId.startsWith("GFILE_")) {
                    val vnId = if (fId.startsWith("VNFILE_")) fId.removePrefix("VNFILE_") else fId.removePrefix("GFILE_")
                    val fragments = fileFragmentDao.getFragmentsForFile(fId, senderId)
                    val dir = File(context.filesDir, "silentnet/voicenotes").apply { mkdirs() }
                    val file = File(dir, "$vnId.m4a"); fileFragmentationManager.reassembleFile(fragments, file)
                    val vn = voiceNoteRepository.findById(vnId)
                    if (vn != null) voiceNoteRepository.update(vn.copy(filePath = file.absolutePath, deliveryState = 2))
                } else if (fId.startsWith("MSGFILE_")) {
                    val rid = fId.removePrefix("MSGFILE_").toLong()
                    val fragments = fileFragmentDao.getFragmentsForFile(fId, senderId)
                    val dir = File(context.filesDir, "silentnet/attachments").apply { mkdirs() }
                    val encFile = File(dir, "TEMP_ENC_$fId")
                    fileFragmentationManager.reassembleFile(fragments, encFile)
                    
                    val senderUsername = nodeIdToUsername[senderId] ?: senderId
                    val contact = contactRepository.findOrCreateContact(myUsername ?: "", senderUsername, senderUsername)
                    val msg = messageRepository.findByRemoteId(contact.id, rid)
                    if (msg != null) {
                        val myPrivKey = identityManager.getPrivateKey()
                        if (myPrivKey != null) {
                            val decFile = File(dir, "${System.currentTimeMillis()}_${msg.attachmentName ?: "attachment"}")
                            val success = com.silentnet.security.CryptographyManager.decryptFile(encFile, decFile, myPrivKey)
                            if (success) {
                                messageRepository.updateAttachmentPath(msg.id, decFile.absolutePath)
                                messageRepository.updateDeliveryStatus(msg.id, 2)
                            }
                            encFile.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun sendFragment(fragment: FileFragmentEntity) {
        scope.launch {
            val packet = JSONObject().apply {
                put("pId", UUID.randomUUID().toString()); put("sId", myNodeId); put("tId", fragment.targetId)
                put("ttl", if (fragment.isGroup) 6 else 12); put("hC", 0); put("ts", System.currentTimeMillis())
                put("pTy", if (fragment.isGroup) "group_file_frag" else "file_frag")
                put("pay", JSONObject().apply { put("fId", fragment.fileId); put("idx", fragment.fragmentIndex); put("tot", fragment.totalFragments); put("sum", fragment.checksum); put("data", android.util.Base64.encodeToString(fragment.data, android.util.Base64.NO_WRAP)) }.toString())
            }.toString()
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_LOW) }
        }
    }

    fun relayLostLinkReport(report: LostLinkReportEntity) {
        scope.launch {
            val packet = JSONObject().apply {
                put("pId", UUID.randomUUID().toString()); put("sId", myNodeId); put("tId", "LOSTLINK_SERVICE"); put("ttl", 12); put("hC", 0); put("ts", System.currentTimeMillis()); put("pTy", "lostlink_report")
                put("pay", JSONObject().apply { put("deviceId", report.deviceId); put("reporter", report.reporterNodeId); put("timestamp", report.timestamp); put("rssi", report.rssi); put("conf", report.confidence); put("info", report.nodeInfo) }.toString())
            }.toString()
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_LOW) }
        }
    }

    fun sendWalkieSegment(channelId: String, file: File, duration: Long) {
        scope.launch {
            val key = walkieDao.findChannelById(channelId)?.channelKey
            val encFile = if (key != null) {
                val f = File(file.parent, "WENC_${file.name}")
                if (com.silentnet.security.CryptographyManager.encryptFileWithGroupKey(file, f, key)) f else file
            } else file
            val segmentId = "WS_${System.currentTimeMillis()}"; val fileId = "WFILE_$segmentId"
            val meta = JSONObject().apply {
                put("pId", segmentId); put("sId", myNodeId); put("tId", "WALKIE_TALKIE"); put("pTy", "walkie_segment")
                put("pay", JSONObject().apply { put("channelId", channelId); put("alias", myUsername ?: "User"); put("ts", System.currentTimeMillis()); put("dur", duration) }.toString())
                put("ttl", 8); put("hC", 0); put("ts", System.currentTimeMillis())
            }.toString()
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(meta.toByteArray(StandardCharsets.UTF_8)), PRIORITY_LOW) }
            fileFragmentationManager.splitFile(encFile, fileId, "WALKIE_TALKIE", true).forEach { sendFragment(it) }
        }
    }

    fun broadcastSearchQuery(queryId: String, query: String) {
        scope.launch {
            val payload = JSONObject().apply { put("qId", queryId); put("q", query) }.toString()
            val packet = wrapInMeshPacket("BROADCAST", "search_query", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_LOW) }
        }
    }

    private suspend fun handleSearchQuery(queryId: String, sourceNodeId: String, query: String) {
        val results = JSONArray()
        recoveryDao.searchPosts(query).first().forEach { results.put(JSONObject().apply { put("type", "Recovery Post"); put("title", it.category); put("content", it.content); put("score", 0.9) }) }
        messageRepository.searchMessages(query).forEach { results.put(JSONObject().apply { put("type", "Message"); put("title", "From ${it.senderLabel}"); put("content", it.body ?: ""); put("score", 0.7) }) }
        
        // LostLink Search Support
        recoveryDao.getLostReports(query).forEach { 
            results.put(JSONObject().apply { 
                put("type", "LostLink Report")
                put("title", "Device: ${it.deviceId}")
                put("content", "Confidence: ${"%.2f".format(it.confidence * 100)}% | RSSI: ${it.rssi} | Node: ${it.reporterNodeId}")
                put("score", 1.0) 
            }) 
        }

        if (results.length() > 0) {
            val payload = JSONObject().apply { put("qId", queryId); put("results", results) }.toString()
            val packet = wrapInMeshPacket(sourceNodeId, "search_results", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
        }
    }

    private fun processLocalGroupPayload(groupId: String, senderNodeId: String, data: String) {
        scope.launch {
            try {
                val json = JSONObject(data); val fromU = json.getString("fromU"); val contact = contactRepository.findOrCreateContact(myUsername ?: "", fromU, json.getString("fromF"))
                messageRepository.insert(MessageEntity(contactId = contact.id, senderLabel = json.getString("fromF"), body = json.optString("body"), isOutgoing = false, deliveryStatus = 2, remoteId = json.getLong("rid"), groupId = groupId, groupKeyId = json.optString("kId")))
            } catch (e: Exception) {}
        }
    }

    private fun processLocalPayload(endpointId: String, data: String) {
        scope.launch {
            try {
                val json = JSONObject(data)
                if (json.optString("type") == "msg") {
                    val fromU = json.getString("fromU"); val contact = contactRepository.findOrCreateContact(myUsername ?: "", fromU, json.getString("fromF"))
                    val msg = MessageEntity(
                        contactId = contact.id,
                        senderLabel = json.getString("fromF"),
                        body = json.optString("body"),
                        attachmentName = json.optString("aNm"),
                        attachmentMime = json.optString("aMi"),
                        priority = json.optInt("pri"),
                        isOutgoing = false,
                        deliveryStatus = 2,
                        remoteId = json.getLong("rid")
                    )
                    messageRepository.insert(msg); sendAck(fromU, msg.remoteId ?: 0)
                }
            } catch (e: Exception) {
                Log.e("SilentNetTransport", "Failed to process local payload: ${e.message}")
            }
        }
    }

    private suspend fun sendAck(targetUsername: String, remoteId: Long) {
        val targetNodeId = usernameToNodeId[targetUsername] ?: targetUsername
        val meshPacket = wrapInMeshPacket(targetNodeId, "ack", JSONObject().apply { put("type", "ack"); put("lid", remoteId) }.toString())
        activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(meshPacket.toByteArray(StandardCharsets.UTF_8)), PRIORITY_CRITICAL) }
    }

    private fun wrapInMeshPacket(targetNodeId: String, payloadType: String, payload: String, packetId: String? = null): String {
        return JSONObject().apply { 
            put("pId", packetId ?: UUID.randomUUID().toString())
            put("sId", myNodeId)
            put("tId", targetNodeId)
            put("ttl", 5)
            put("hC", 0)
            put("ts", System.currentTimeMillis())
            put("pTy", payloadType)
            put("pay", payload) 
        }.toString()
    }

    fun sendMessage(username: String, message: MessageEntity, packetId: String? = null) {
        scope.launch {
            val targetNodeId = usernameToNodeId[username] ?: username
            val payload = JSONObject().apply {
                put("type", "msg")
                put("fromU", myUsername)
                put("fromF", myFullName)
                put("body", message.body ?: "")
                put("rid", message.id)
                put("aNm", message.attachmentName)
                put("aMi", message.attachmentMime)
                put("pri", message.priority)
            }.toString()

            val recipientPk = peerPublicKeys[targetNodeId] ?: peerPublicKeys[username] ?: contactRepository.contacts(myUsername ?: "").find { it.contactUsername == username }?.publicKey
            val encrypted = if (recipientPk != null) com.silentnet.security.CryptographyManager.encryptPayload(payload, recipientPk) ?: payload else payload
            
            val meshPacket = wrapInMeshPacket(targetNodeId, "msg", encrypted, packetId)
            val connections = activeConnections.keys
            if (connections.isNotEmpty()) {
                connections.forEach { enqueuePayload(it, Payload.fromBytes(meshPacket.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
                messageRepository.updateDeliveryStatus(message.id, 1)

                // Handle Attachment Fragmentation
                if (message.attachmentPath != null) {
                    val file = java.io.File(message.attachmentPath)
                    if (file.exists()) {
                        val encryptedFile = java.io.File(context.cacheDir, "SEND_ENC_${message.id}")
                        val success = if (recipientPk != null) {
                            com.silentnet.security.CryptographyManager.encryptFile(file, encryptedFile, recipientPk)
                        } else {
                            file.copyTo(encryptedFile, true)
                            true
                        }

                        if (success) {
                            val fragments = fileFragmentationManager.splitFile(encryptedFile, "MSGFILE_${message.id}", targetNodeId, false)
                            fragments.forEach { sendFragment(it) }
                        }
                    }
                }
            }
        }
    }

    fun sendGroupMessage(groupId: String, message: MessageEntity) {
        scope.launch {
            val group = groupRepository.findGroupById(groupId) ?: return@launch
            val keyId = group.currentKeyId ?: return@launch
            val groupKeyEntity = groupRepository.findKey(groupId, keyId) ?: return@launch
            val myPrivKey = identityManager.getPrivateKey() ?: return@launch
            val decryptedKeyBase64 = com.silentnet.security.CryptographyManager.decryptPayload(groupKeyEntity.encryptedKey, myPrivKey) ?: return@launch
            val keyBytes = android.util.Base64.decode(decryptedKeyBase64, android.util.Base64.DEFAULT)
            val payload = JSONObject().apply { put("fromU", myUsername); put("fromF", myFullName); put("body", message.body ?: ""); put("rid", message.id); put("kId", keyId) }.toString()
            val encrypted = com.silentnet.security.CryptographyManager.encryptWithGroupKey(payload, keyBytes) ?: return@launch
            val packet = JSONObject().apply { put("pId", UUID.randomUUID().toString()); put("sId", myNodeId); put("tId", groupId); put("ttl", 5); put("hC", 0); put("ts", System.currentTimeMillis()); put("pTy", "group_msg"); put("kId", keyId); put("pay", encrypted) }.toString()
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
            messageRepository.updateDeliveryStatus(message.id, 1)
        }
    }

    fun sendEmergencyBroadcast(title: String, body: String) {
        scope.launch {
            val owner = myUsername ?: return@launch
            val msg = MessageEntity(contactId = -1, senderLabel = myFullName ?: owner, body = body, emergencyTitle = title, isOutgoing = true, isEmergency = true, timestamp = System.currentTimeMillis())
            val id = messageRepository.insert(msg)
            val packet = wrapInMeshPacket("BROADCAST", "emergency", JSONObject().apply { put("type", "emergency"); put("title", title); put("body", body); put("rid", id) }.toString())
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_CRITICAL) }
        }
    }

    fun joinRecoveryGroup(groupId: String) {
        scope.launch {
            recoveryDao.updateJoinStatus(groupId, true)
        }
    }

    fun sendRecoveryPost(category: String, content: String, priority: Int, isAnonymous: Boolean) {
        scope.launch {
            val postId = UUID.randomUUID().toString()
            val author = if (isAnonymous) "Anonymous" else (myUsername ?: "Unknown")
            val post = RecoveryPostEntity(
                postId = postId,
                category = category,
                content = content,
                authorAlias = if (isAnonymous) "Anonymous Mesh Node" else (myFullName ?: author),
                authorNodeId = myNodeId ?: author,
                timestamp = System.currentTimeMillis(),
                priority = priority,
                isAnonymous = isAnonymous,
                expiration = System.currentTimeMillis() + 86400000
            )
            recoveryDao.insertPost(post)
            
            val payload = JSONObject().apply {
                put("id", postId); put("cat", category); put("txt", content); put("aut", post.authorAlias); put("aid", post.authorNodeId); put("ts", post.timestamp); put("pri", priority); put("anon", isAnonymous)
            }.toString()
            val packet = wrapInMeshPacket("BROADCAST", "recovery_post", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
        }
    }

    fun createRecoveryGroup(name: String, description: String?) {
        scope.launch {
            val groupId = UUID.randomUUID().toString()
            val group = RecoveryGroupEntity(groupNodeId = groupId, name = name, description = description, creatorNodeId = myNodeId ?: "Unknown", createdAt = System.currentTimeMillis(), isJoined = true)
            recoveryDao.insertGroup(group)
            
            val payload = JSONObject().apply { put("id", groupId); put("nm", name); put("ds", description ?: ""); put("cid", group.creatorNodeId); put("ts", group.createdAt) }.toString()
            val packet = wrapInMeshPacket("BROADCAST", "recovery_group", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
        }
    }

    fun sendGroupLeavePacket(groupId: String) {
        scope.launch {
            val payload = JSONObject().apply { put("gId", groupId); put("id", myNodeId ?: "Unknown") }.toString()
            val packet = wrapInMeshPacket("BROADCAST", "group_leave", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
            groupRepository.leaveGroup(groupId)
        }
    }
    fun inviteMember(groupId: String, username: String) {
        scope.launch {
            try {
                val group = groupRepository.findGroupById(groupId) ?: return@launch
                val targetNodeId = usernameToNodeId[username] ?: username
                
                groupRepository.insertMember(
                    GroupMemberEntity(
                        groupId = groupId,
                        nodeId = targetNodeId,
                        alias = username,
                        role = 0
                    )
                )

                val currentKeyId = group.currentKeyId ?: return@launch
                val groupKeyEntity = groupRepository.findKey(groupId, currentKeyId) ?: return@launch
                val myPrivKey = identityManager.getPrivateKey() ?: return@launch
                
                val decryptedKeyBase64 = com.silentnet.security.CryptographyManager.decryptPayload(groupKeyEntity.encryptedKey, myPrivKey)
                val recipientPk = peerPublicKeys[targetNodeId] ?: contactRepository.contacts(myUsername ?: "").find { it.contactUsername == username }?.publicKey
                
                if (decryptedKeyBase64 != null && recipientPk != null) {
                    val encryptedKeyForMember = com.silentnet.security.CryptographyManager.encryptPayload(decryptedKeyBase64, recipientPk)
                    if (encryptedKeyForMember != null) {
                        val payload = JSONObject().apply {
                            put("gId", groupId)
                            put("nm", group.name)
                            put("ds", group.description ?: "")
                            put("kId", currentKeyId)
                            put("eK", encryptedKeyForMember)
                        }.toString()
                        
                        val packet = wrapInMeshPacket(targetNodeId, "group_invite", payload)
                        activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
                        Log.d("SilentNetGroup", "Sent invitation to $username for group ${group.name}")
                    }
                }

                Log.d("SilentNetGroup", "Invited $username to group $groupId")
            } catch (e: Exception) {
                Log.e("SilentNetGroup", "Invite failed", e)
            }
        }
    }
    fun sendVoiceNote(recipientNodeId: String?, groupId: String?, file: File, duration: Long) {
        scope.launch {
            val vnId = "VN_${UUID.randomUUID()}"
            val target = groupId ?: recipientNodeId ?: "BROADCAST"
            
            voiceNoteRepository.insert(VoiceNoteEntity(voiceNoteId = vnId, sender = myNodeId ?: "Unknown", recipient = recipientNodeId, groupId = groupId, duration = duration, size = file.length(), filePath = file.absolutePath, deliveryState = 1))
            
            val metaPayload = JSONObject().apply {
                put("vnId", vnId); put("dur", duration); put("size", file.length()); put("s", myNodeId); put("r", recipientNodeId ?: ""); put("g", groupId ?: "")
            }.toString()
            val metaPacket = wrapInMeshPacket(target, "voice_note_meta", metaPayload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(metaPacket.toByteArray(StandardCharsets.UTF_8)), PRIORITY_LOW) }
            
            fileFragmentationManager.splitFile(file, "VNFILE_$vnId", target, groupId != null).forEach { sendFragment(it) }
        }
    }

    fun requestMissingFragments(fileId: String, targetNodeId: String) {
        scope.launch {
            val total = fileFragmentDao.getTotalFragments(fileId)
            val received = fileFragmentDao.getReceivedFragmentIndices(fileId)
            val missing = (0 until total).filter { !received.contains(it) }
            
            if (missing.isNotEmpty()) {
                val payload = JSONObject().apply { put("fId", fileId); put("mis", JSONArray(missing)) }.toString()
                val packet = wrapInMeshPacket(targetNodeId, "frag_request", payload)
                activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
            }
        }
    }

    fun sendWalkiePresence(channelId: String) {
        scope.launch {
            val payload = JSONObject().apply { put("cId", channelId); put("u", myUsername ?: "User"); put("id", myNodeId ?: "Unknown") }.toString()
            val packet = wrapInMeshPacket("WALKIE_TALKIE", "walkie_presence", payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_MEDIUM) }
        }
    }
    fun sendCallSignaling(targetUsername: String, type: String, payload: String) {
        scope.launch {
            val targetNodeId = usernameToNodeId[targetUsername] ?: targetUsername
            val packet = wrapInMeshPacket(targetNodeId, type, payload)
            activeConnections.keys.forEach { enqueuePayload(it, Payload.fromBytes(packet.toByteArray(StandardCharsets.UTF_8)), PRIORITY_CRITICAL) }
            Log.d("SilentNetTransport", "Signaling $type sent to $targetUsername")
        }
    }

    private fun cleanupTempFiles() {
        File(context.cacheDir, "temp").apply {
            if (exists()) {
                listFiles()?.forEach { it.delete() }
            }
        }
    }

}
