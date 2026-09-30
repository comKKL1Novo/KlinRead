package com.klin.read.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/**
 * A locally stored profile.
 *
 * There is no server and no verification: this is a local identity label, not a
 * credential. [passwordHash] exists so a password is not sitting in plain text on
 * disk, but it is a plain one-way digest with no salt or stretching, which is not
 * sufficient for anything security-sensitive. Treat this as a nickname system.
 */
data class Account(
    val id: String,
    val displayName: String,
    val passwordHash: String,
    val avatarSeed: Int,
    val avatarImageUri: String?,
    val createdAt: Long,
    val bio: String = ""
)

/**
 * Stores exactly one local account.
 *
 * Single-account by design: the request was for a profile the user creates and
 * edits, not a multi-user switcher, and one row avoids a whole class of
 * "which account am I editing" ambiguity.
 */
class AccountStore(private val context: Context) {

    private object Keys {
        val ID = stringPreferencesKey("account_id")
        val NAME = stringPreferencesKey("account_name")
        val HASH = stringPreferencesKey("account_password")
        val AVATAR_SEED = stringPreferencesKey("account_avatar_seed")
        val AVATAR_URI = stringPreferencesKey("account_avatar_uri")
        val CREATED = stringPreferencesKey("account_created")
        val BIO = stringPreferencesKey("account_bio")
    }

    val account: Flow<Account?> = context.accountStore.data.map { prefs ->
        val id = prefs[Keys.ID] ?: return@map null
        Account(
            id = id,
            displayName = prefs[Keys.NAME] ?: "未命名",
            passwordHash = prefs[Keys.HASH] ?: "",
            avatarSeed = prefs[Keys.AVATAR_SEED]?.toIntOrNull() ?: 0,
            avatarImageUri = prefs[Keys.AVATAR_URI]?.takeIf { it.isNotBlank() },
            createdAt = prefs[Keys.CREATED]?.toLongOrNull() ?: System.currentTimeMillis(),
            bio = prefs[Keys.BIO] ?: ""
        )
    }

    /** Whether any account exists yet, used to choose between sign-up and sign-in. */
    val hasAccount: Flow<Boolean> = account.map { it != null }

    suspend fun create(
        displayName: String,
        password: String,
        avatarSeed: Int
    ): Account {
        val created = Account(
            id = generateId(),
            displayName = displayName.trim(),
            passwordHash = hash(password),
            avatarSeed = avatarSeed,
            avatarImageUri = null,
            createdAt = System.currentTimeMillis()
        )
        write(created)
        return created
    }

    suspend fun update(account: Account) = write(account)

    suspend fun signOut() {
        context.accountStore.edit { it.clear() }
    }

    /** Verifies against the stored digest. Returns true when it matches. */
    suspend fun verify(password: String, stored: Account): Boolean =
        stored.passwordHash == hash(password)

    private suspend fun write(account: Account) {
        context.accountStore.edit { prefs ->
            prefs[Keys.ID] = account.id
            prefs[Keys.NAME] = account.displayName
            prefs[Keys.HASH] = account.passwordHash
            prefs[Keys.AVATAR_SEED] = account.avatarSeed.toString()
            prefs[Keys.AVATAR_URI] = account.avatarImageUri.orEmpty()
            prefs[Keys.CREATED] = account.createdAt.toString()
            prefs[Keys.BIO] = account.bio
        }
    }

    private fun generateId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }

    /**
     * A one-way digest of the password.
     *
     * MD5 is used because it needs no dependency and this is not a security
     * boundary. Its only purpose is to avoid writing the literal password to
     * disk. Anyone with access to the device's app data can invert it trivially.
     */
    private fun hash(password: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
