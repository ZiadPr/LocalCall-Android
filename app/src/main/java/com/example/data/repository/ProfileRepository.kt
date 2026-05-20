package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "profile_prefs")

class ProfileRepository(private val context: Context) {
    companion object {
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
    }

    val userIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val usernameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USERNAME]
    }

    suspend fun hasProfile(): Boolean {
        val prefs = context.dataStore.data.map { it[KEY_USERNAME] }.firstOrNull()
        return !prefs.isNullOrBlank()
    }

    suspend fun saveProfile(username: String): String {
        var finalId = context.dataStore.data.map { it[KEY_USER_ID] }.firstOrNull()
        if (finalId.isNullOrEmpty()) {
            finalId = UUID.randomUUID().toString()
        }
        val idToSave = finalId
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = idToSave
            preferences[KEY_USERNAME] = username
        }
        return idToSave
    }

    suspend fun getProfile(): Pair<String, String>? {
        val prefs = context.dataStore.data.firstOrNull() ?: return null
        val id = prefs[KEY_USER_ID] ?: return null
        val name = prefs[KEY_USERNAME] ?: return null
        return Pair(id, name)
    }
}
