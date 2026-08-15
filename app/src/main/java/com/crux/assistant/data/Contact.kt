package com.crux.assistant.data

/**
 * Contact.kt
 *
 * One entry in the user's manually-managed name -> number mapping (feature 4).
 * This is intentionally NOT backed by Android's Contacts provider — CRUX never
 * requests READ_CONTACTS. The user types these in themselves via ContactMappingActivity,
 * so only the names/numbers they explicitly choose to give CRUX are ever known to it.
 */
data class Contact(
    val name: String,       // stored lowercase-normalized for matching against speech
    val phoneNumber: String
)
