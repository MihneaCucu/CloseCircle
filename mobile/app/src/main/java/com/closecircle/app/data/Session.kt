package com.closecircle.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface TokenStore {
    suspend fun read(): String?
    suspend fun write(token: String?)
}

// shortcut: the JWT sits in the app-private DataStore (not readable by other apps); encrypt it with the
// Android Keystore before a release build.
class DataStoreTokenStore(private val store: DataStore<Preferences>) : TokenStore {
    private val key = stringPreferencesKey("access_token")

    override suspend fun read(): String? = store.data.first()[key]

    override suspend fun write(token: String?) {
        store.edit { if (token == null) it.remove(key) else it[key] = token }
    }
}

/** The signed-in user's access token. [token] is read by the HTTP client on every request. */
class Session(private val store: TokenStore) {
    @Volatile
    var token: String? = null
        private set

    suspend fun load() {
        token = store.read()
    }

    suspend fun signIn(accessToken: String) {
        token = accessToken
        store.write(accessToken)
    }

    suspend fun signOut() {
        token = null
        store.write(null)
    }
}
