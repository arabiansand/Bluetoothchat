package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

class UserRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nearby_chat_identity", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(loadOrCreateUser())
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private fun loadOrCreateUser(): User {
        val existingId = prefs.getString(KEY_USER_ID, null)
        val existingName = prefs.getString(KEY_DISPLAY_NAME, null)
        val existingCreatedAt = prefs.getLong(KEY_CREATED_AT, 0L)

        return if (existingId != null && existingName != null && existingCreatedAt != 0L) {
            User(id = existingId, displayName = existingName, createdAt = existingCreatedAt)
        } else {
            // Generate privacy-friendly anonymous user ID (e.g. 8-char hex)
            val newId = UUID.randomUUID().toString().replace("-", "").take(8)
            // Generate default display name: "User " + random 4-digit number
            val randomNum = Random.nextInt(1000, 9999)
            val newName = "User $randomNum"
            val now = System.currentTimeMillis()

            prefs.edit()
                .putString(KEY_USER_ID, newId)
                .putString(KEY_DISPLAY_NAME, newName)
                .putLong(KEY_CREATED_AT, now)
                .apply()

            User(id = newId, displayName = newName, createdAt = now)
        }
    }

    fun updateDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val current = _currentUser.value
        val updated = current.copy(displayName = trimmed)
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        _currentUser.value = updated
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_CREATED_AT = "created_at"
    }
}
