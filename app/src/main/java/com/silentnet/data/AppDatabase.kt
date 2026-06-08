package com.silentnet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory
import com.silentnet.auth.SessionManager

@Database(
    entities = [
        UserEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        MeshPacketEntity::class,
        RecoveryPostEntity::class,
        RecoveryGroupEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupKeyEntity::class,
        MeshAnalyticsEntity::class,
        NetworkEventEntity::class,
        TrustedAdminEntity::class,
        NodeReputationEntity::class,
        ResearchMetricEntity::class,
        MissingPersonEntity::class,
        SafeZoneEntity::class,
        MedicalAssistanceEntity::class,
        ResourceEntity::class,
        VolunteerEntity::class,
        IncidentReportEntity::class,
        LostDeviceEntity::class,
        LostLinkReportEntity::class,
        SearchHistoryEntity::class,
        SearchResultEntity::class,
        WalkieChannelEntity::class,
        WalkieSegmentEntity::class,
        FileFragmentEntity::class,
        VoiceNoteEntity::class
    ],
    version = 21,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun meshPacketDao(): MeshPacketDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun groupDao(): GroupDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun trustedAdminDao(): TrustedAdminDao
    abstract fun reputationDao(): ReputationDao
    abstract fun researchDao(): ResearchDao
    abstract fun disasterDao(): DisasterDao
    abstract fun walkieDao(): WalkieDao
    abstract fun fileFragmentDao(): FileFragmentDao
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, sessionManager: SessionManager): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE != null) return@synchronized INSTANCE!!
                
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
                val passphrase = sessionManager.getOrCreateDatabaseKey()
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silentnet.db"
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration() // Handle migration failures by resetting
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                    MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21
                    )
                .build()
                
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `voice_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `voiceNoteId` TEXT NOT NULL, `sender` TEXT NOT NULL, `recipient` TEXT, `groupId` TEXT, `duration` INTEGER NOT NULL, `size` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `isEncrypted` INTEGER NOT NULL, `deliveryState` INTEGER NOT NULL, `isFragmented` INTEGER NOT NULL, `totalFragments` INTEGER NOT NULL, `receivedFragments` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_voice_notes_voiceNoteId` ON `voice_notes` (`voiceNoteId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_notes_sender` ON `voice_notes` (`sender`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_notes_recipient` ON `voice_notes` (`recipient`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_voice_notes_groupId` ON `voice_notes` (`groupId`)")
            }
        }

        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `search_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `queryId` TEXT NOT NULL, `sourceNodeId` TEXT NOT NULL, `targetType` TEXT NOT NULL, `content` TEXT NOT NULL, `title` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `rankingScore` REAL NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_search_results_queryId` ON `search_results` (`queryId`)")
            }
        }

        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `walkie_channels` ADD COLUMN `channelKey` BLOB")
            }
        }

        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `file_fragments` ADD COLUMN `targetId` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `file_fragments` ADD COLUMN `isGroup` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `file_fragments` ADD COLUMN `senderNodeId` TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_file_fragments_targetId` ON `file_fragments` (`targetId`)")
            }
        }

        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_query` ON `search_history` (`query`)")
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `lost_devices` ADD COLUMN `secret` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `lost_devices` ADD COLUMN `currentAnonymousId` TEXT")
            }
        }

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `file_fragments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileId` TEXT NOT NULL, `fragmentIndex` INTEGER NOT NULL, `totalFragments` INTEGER NOT NULL, `data` BLOB NOT NULL, `checksum` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_file_fragments_fileId` ON `file_fragments` (`fileId`)")
            }
        }

        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `walkie_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `channelId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `isJoined` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_walkie_channels_channelId` ON `walkie_channels` (`channelId`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `walkie_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` TEXT NOT NULL, `channelId` TEXT NOT NULL, `senderNodeId` TEXT NOT NULL, `senderAlias` TEXT NOT NULL, `filePath` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `duration` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_walkie_segments_segmentId` ON `walkie_segments` (`segmentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_walkie_segments_channelId` ON `walkie_segments` (`channelId`)")
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `lost_devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` TEXT NOT NULL, `deviceName` TEXT NOT NULL, `isLost` INTEGER NOT NULL, `lastSeenTime` INTEGER, `lastSeenLocation` TEXT, `createdAt` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lost_devices_deviceId` ON `lost_devices` (`deviceId`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `lostlink_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` TEXT NOT NULL, `reporterNodeId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `rssi` INTEGER NOT NULL, `confidence` REAL NOT NULL, `nodeInfo` TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lostlink_reports_deviceId` ON `lostlink_reports` (`deviceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_lostlink_reports_reporterNodeId` ON `lostlink_reports` (`reporterNodeId`)")
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `missing_persons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` TEXT NOT NULL, `reporterNodeId` TEXT NOT NULL, `personName` TEXT NOT NULL, `age` TEXT NOT NULL, `gender` TEXT NOT NULL, `photoReference` TEXT, `description` TEXT NOT NULL, `lastKnownLocation` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_missing_persons_reportId` ON `missing_persons` (`reportId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_missing_persons_status` ON `missing_persons` (`status`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `safe_zones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `zoneId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `capacity` INTEGER NOT NULL, `occupancy` INTEGER NOT NULL, `contactInfo` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_safe_zones_zoneId` ON `safe_zones` (`zoneId`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `medical_assistance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `requestId` TEXT NOT NULL, `patientType` TEXT NOT NULL, `urgency` TEXT NOT NULL, `medicalNeed` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_medical_assistance_requestId` ON `medical_assistance` (`requestId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_medical_assistance_urgency` ON `medical_assistance` (`urgency`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `disaster_resources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `resourceId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `location` TEXT NOT NULL, `priorityTag` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_disaster_resources_resourceId` ON `disaster_resources` (`resourceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_disaster_resources_category` ON `disaster_resources` (`category`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `volunteers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `volunteerId` TEXT NOT NULL, `nodeId` TEXT NOT NULL, `skills` TEXT NOT NULL, `availability` TEXT NOT NULL, `locationArea` TEXT NOT NULL, `category` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_volunteers_volunteerId` ON `volunteers` (`volunteerId`)")
                
                database.execSQL("CREATE TABLE IF NOT EXISTS `incident_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reportId` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `location` TEXT NOT NULL, `priority` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_incident_reports_reportId` ON `incident_reports` (`reportId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_reports_type` ON `incident_reports` (`type`)")
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `node_reputation` (`nodeId` TEXT NOT NULL, `successCount` INTEGER NOT NULL, `failureCount` INTEGER NOT NULL, `packetsRelayed` INTEGER NOT NULL, `packetsDropped` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `stabilityScore` REAL NOT NULL, `contributionScore` REAL NOT NULL, PRIMARY KEY(`nodeId`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `research_metrics` (`date` TEXT NOT NULL, `gossipEfficiency` REAL NOT NULL, `averageReputation` REAL NOT NULL, `congestionScore` REAL NOT NULL, `relayLoadBalance` REAL NOT NULL, `deliveryProbability` REAL NOT NULL, `packetsSuppressed` INTEGER NOT NULL, `totalGossipPackets` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                database.execSQL("ALTER TABLE `mesh_packets` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `mesh_packets` ADD COLUMN `predictionScore` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `trusted_admins` (`publicKeyBase64` TEXT NOT NULL, `alias` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`publicKeyBase64`))")
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `users` ADD COLUMN `passwordSalt` TEXT")
                database.execSQL("ALTER TABLE `users` ADD COLUMN `passwordIterations` INTEGER NOT NULL DEFAULT 100000")
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mesh_analytics` (`date` TEXT NOT NULL, `messagesSent` INTEGER NOT NULL, `messagesDelivered` INTEGER NOT NULL, `messagesFailed` INTEGER NOT NULL, `averageDeliveryTime` INTEGER NOT NULL, `averageHopCount` REAL NOT NULL, `averageRouteQuality` REAL NOT NULL, `packetsRelayed` INTEGER NOT NULL, `emergencyRelays` INTEGER NOT NULL, `storeForwardDeliveries` INTEGER NOT NULL, `groupRelays` INTEGER NOT NULL, `recoveryRelays` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `network_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `recovery_posts` ADD COLUMN `signature` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentPath` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentName` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentMime` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `remoteId` INTEGER")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isViewOnce` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isConsumed` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isEmergency` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `emergencySignature` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `emergencyTitle` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `isAcknowledged` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `groupId` TEXT")
                database.execSQL("ALTER TABLE `messages` ADD COLUMN `groupKeyId` TEXT")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `groups` (`groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `creatorNodeId` TEXT NOT NULL, `type` INTEGER NOT NULL, `currentKeyId` TEXT, `isJoined` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `group_members` (`groupId` TEXT NOT NULL, `nodeId` TEXT NOT NULL, `alias` TEXT NOT NULL, `role` INTEGER NOT NULL, `joinTime` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `nodeId`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `group_keys` (`groupId` TEXT NOT NULL, `keyId` TEXT NOT NULL, `encryptedKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `keyId`))")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `recovery_posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `postId` TEXT NOT NULL, `authorNodeId` TEXT NOT NULL, `authorAlias` TEXT NOT NULL, `category` TEXT NOT NULL, `content` TEXT NOT NULL, `priority` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `expiration` INTEGER NOT NULL, `isAnonymous` INTEGER NOT NULL DEFAULT 0, `isLocal` INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `recovery_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupNodeId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `creatorNodeId` TEXT NOT NULL, `isJoined` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `mesh_packets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packetId` TEXT NOT NULL, `sourceNodeId` TEXT NOT NULL, `targetNodeId` TEXT NOT NULL, `payloadType` TEXT NOT NULL, `sPk` TEXT, `encryptedPacketJson` TEXT NOT NULL, `ttl` INTEGER NOT NULL, `hopCount` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `expirationTime` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL DEFAULT 0)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mesh_packets_packetId` ON `mesh_packets` (`packetId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_packets_targetNodeId` ON `mesh_packets` (`targetNodeId`)")
            }
        }
    }
}
