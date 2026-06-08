package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(
    private val contactDao: ContactDao,
    private val messageDao: MessageDao
) {

    fun observeContacts(ownerUsername: String) = contactDao.observeContacts(ownerUsername)
    fun observeAllMessages(ownerUsername: String) = messageDao.observeAllMessages(ownerUsername)
    fun observeAttachedMessages(ownerUsername: String) = messageDao.observeAttachedMessages(ownerUsername)

    suspend fun seedDemoContacts(ownerUsername: String) {
        withContext(Dispatchers.IO) {
            if (contactDao.count(ownerUsername) == 0) {
                listOf(
                    "Campus Desk" to "campus_desk",
                    "Astra" to "astra",
                    "Ops Relay" to "ops_relay"
                ).forEach { (alias, username) ->
                    contactDao.insert(
                        ContactEntity(
                            ownerUsername = ownerUsername,
                            contactUsername = username,
                            alias = alias,
                            autoReplyEnabled = true
                        )
                    )
                }
            }
        }
    }

    suspend fun findOrCreateContact(ownerUsername: String, contactUsername: String, alias: String, publicKey: String? = null): ContactEntity {
        return withContext(Dispatchers.IO) {
            val existing = contactDao.findByContactUsername(ownerUsername, contactUsername)
            if (existing != null) {
                if (publicKey != null && existing.publicKey != publicKey) {
                    val updated = existing.copy(publicKey = publicKey)
                    contactDao.insert(updated)
                    return@withContext updated
                }
                return@withContext existing
            }

            val id = contactDao.insert(
                ContactEntity(
                    ownerUsername = ownerUsername,
                    contactUsername = contactUsername,
                    alias = alias,
                    publicKey = publicKey
                )
            )
            contactDao.contacts(ownerUsername).find { it.id == id }!!
        }
    }

    suspend fun addContact(ownerUsername: String, contactUsername: String, alias: String, publicKey: String? = null): Long {
        return withContext(Dispatchers.IO) {
            contactDao.insert(
                ContactEntity(
                    ownerUsername = ownerUsername,
                    contactUsername = contactUsername.trim(),
                    alias = alias.trim(),
                    publicKey = publicKey
                )
            )
        }
    }

    suspend fun updatePublicKey(contactId: Long, publicKey: String) {
        withContext(Dispatchers.IO) {
            val contact = contactDao.findById(contactId)
            if (contact != null && contact.publicKey != publicKey) {
                contactDao.insert(contact.copy(publicKey = publicKey))
            }
        }
    }

    suspend fun updatePinnedStatus(contactId: Long, isPinned: Boolean) {
        withContext(Dispatchers.IO) {
            contactDao.updatePinnedStatus(contactId, isPinned)
        }
    }

    suspend fun count(ownerUsername: String): Int {
        return withContext(Dispatchers.IO) {
            contactDao.count(ownerUsername)
        }
    }

    suspend fun contacts(ownerUsername: String): List<ContactEntity> {
        return withContext(Dispatchers.IO) {
            contactDao.contacts(ownerUsername)
        }
    }

    suspend fun findById(contactId: Long): ContactEntity? {
        return withContext(Dispatchers.IO) {
            contactDao.findById(contactId)
        }
    }

    suspend fun clearOwnerData(ownerUsername: String) {
        withContext(Dispatchers.IO) {
            messageDao.deleteForOwner(ownerUsername)
            contactDao.deleteForOwner(ownerUsername)
        }
    }
}
