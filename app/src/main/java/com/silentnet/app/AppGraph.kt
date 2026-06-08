package com.silentnet.app

import android.content.Context
import com.silentnet.auth.AuthRepository
import com.silentnet.auth.SessionManager
import com.silentnet.data.AppDatabase
import com.silentnet.data.ContactRepository
import com.silentnet.data.GroupRepository
import com.silentnet.data.MessageRepository
import com.silentnet.emergency.EmergencyAlertManager
import com.silentnet.python.PythonBridge
import com.silentnet.security.IdentityManager
import com.silentnet.transport.TransportManager

class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val sessionManager = SessionManager(appContext)
    val database = AppDatabase.getInstance(appContext, sessionManager)
    val identityManager = IdentityManager()
    val authRepository = AuthRepository(database.userDao(), sessionManager)
    val contactRepository = ContactRepository(database.contactDao(), database.messageDao())
    val messageRepository = MessageRepository(database.messageDao())
    val groupRepository = GroupRepository(database.groupDao())
    val recoveryRepository = com.silentnet.data.RecoveryRepository(database.recoveryDao())
    val walkieRepository = com.silentnet.data.WalkieRepository(database.walkieDao())
    val voiceNoteRepository = com.silentnet.data.VoiceNoteRepository(database.voiceNoteDao())
    val callManager = com.silentnet.transport.CallManager(appContext)
    val fileFragmentationManager = com.silentnet.util.FileFragmentationManager()
    val lostLinkBridge = com.silentnet.lostlink.integration.LostLinkBridge(appContext)

    val transportManager = TransportManager(
        appContext,
        contactRepository,
        messageRepository,
        groupRepository,
        identityManager,
        database.meshPacketDao(),
        database.recoveryDao(),
        database.analyticsDao(),
        database.trustedAdminDao(),
        database.reputationDao(),
        database.researchDao(),
        database.disasterDao(),
        database.walkieDao(),
        voiceNoteRepository,
        database.fileFragmentDao(),
        fileFragmentationManager
    )
    val analyticsManager = com.silentnet.analytics.MeshAnalyticsManager(
        transportManager, database.analyticsDao(), database.messageDao(),
        database.meshPacketDao(), database.groupDao(), database.recoveryDao(), database.researchDao(),
        database.disasterDao()
    )

    val demoManager = com.silentnet.analytics.MeshDemoManager()
    val emergencyAlertManager = EmergencyAlertManager(appContext)
    val pythonBridge = PythonBridge()
    val lostLinkManager = com.silentnet.transport.LostLinkManager(appContext, this)
    val voiceNoteManager = com.silentnet.util.VoiceNoteManager(appContext)

    init {
        transportManager.setEmergencyAlertManager(emergencyAlertManager)
        
        // Connect CallManager and TransportManager
        callManager.onSignalingPacket = { target, type, payload ->
            transportManager.sendCallSignaling(target, type, payload)
        }

        callManager.onStreamPacket = { target, inputStream ->
            transportManager.sendStream(target, inputStream)
        }
        
        transportManager.callSignalingListener = { fromU, fromF, type, payload ->
            when (type) {
                "call_request" -> callManager.handleIncomingCallRequest(fromU, fromF, payload)
                "call_accept" -> callManager.handleCallAccepted(fromU)
                "call_decline" -> callManager.handleCallDeclined(fromU)
                "call_end" -> callManager.handleCallEnded(fromU)
            }
        }

        transportManager.onStreamReceived = { endpointId, inputStream ->
            callManager.handleIncomingStream(endpointId, inputStream)
        }

        transportManager.onPeerDisconnected = { nodeId ->
            if (callManager.currentState != com.silentnet.transport.CallState.IDLE) {
                // If the disconnected peer was the one in the call, end it
                // We should ideally check if it matches remoteUsername, but CallManager handles IDLE check.
                callManager.endCall()
            }
        }
    }
}
