package com.closecircle.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Settings kept on the phone. Elderly mode is off until the user turns it on, and stays as chosen after the app is closed. */
class UserPrefs(private val store: DataStore<Preferences>) {
    private val elderKey = booleanPreferencesKey("elder_mode")

    val elder: Flow<Boolean> = store.data.map { it[elderKey] ?: false }

    suspend fun setElder(on: Boolean) {
        store.edit { it[elderKey] = on }
    }
}
