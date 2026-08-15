package com.crux.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One DataStore file for the whole app, scoped by Context extension property.
private val Context.contactDataStore by preferencesDataStore(name = "crux_contacts")

/**
 * ContactStore.kt
 *
 * Persists the user's manual name -> number mappings ON THIS DEVICE ONLY.
 * - No network calls of any kind happen here.
 * - No READ_CONTACTS permission is used or needed.
 * - Data lives in Jetpack DataStore, which Android already sandboxes to this app's
 *   private storage (same protection level as a private SQLite database).
 *
 * Each contact is encoded as "name::number" in a single string-set preference. That's a
 * deliberately simple format so a beginner can see exactly what's being stored and why,
 * rather than reaching for a full Room database for what is a tiny, local list.
 */
class ContactStore(private val context: Context) {

    private val contactsKey = stringSetPreferencesKey("contacts")

    /** Live stream of the current contact list, sorted by name. */
    val contacts: Flow<List<Contact>> = context.contactDataStore.data.map { prefs ->
        val raw = prefs[contactsKey] ?: emptySet()
        raw.mapNotNull { decode(it) }.sortedBy { it.name }
    }

    /** Adds a contact, or overwrites the number if that name already exists. */
    suspend fun upsert(name: String, phoneNumber: String) {
        val normalizedName = name.trim().lowercase()
        context.contactDataStore.edit { prefs ->
            val current = (prefs[contactsKey] ?: emptySet())
                .mapNotNull { decode(it) }
                .filter { it.name != normalizedName } // drop any existing entry for this name
            val updated = current + Contact(normalizedName, phoneNumber.trim())
            prefs[contactsKey] = updated.map { encode(it) }.toSet()
        }
    }

    suspend fun remove(name: String) {
        val normalizedName = name.trim().lowercase()
        context.contactDataStore.edit { prefs ->
            val current = (prefs[contactsKey] ?: emptySet()).mapNotNull { decode(it) }
            prefs[contactsKey] = current.filter { it.name != normalizedName }
                .map { encode(it) }.toSet()
        }
    }

    /**
     * Looks up a spoken name against the saved mapping. Returns null if there's no match —
     * CommandProcessor/ActionExecutor must treat that as "ask the user to add them first",
     * never as a reason to guess or search elsewhere.
     */
    suspend fun findByName(spokenName: String): Contact? {
        val normalized = spokenName.trim().lowercase()
        return contacts.first().firstOrNull { it.name == normalized }
    }

    private fun encode(contact: Contact) = "${contact.name}::${contact.phoneNumber}"

    private fun decode(raw: String): Contact? {
        val parts = raw.split("::", limit = 2)
        return if (parts.size == 2) Contact(parts[0], parts[1]) else null
    }
}
