package com.closecircle.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DataStoreTokenStore
import com.closecircle.app.data.Session
import com.closecircle.app.data.UserPrefs
import com.closecircle.app.data.api.ApiClient
import com.closecircle.app.data.api.CloseCircleApi
import com.closecircle.app.data.api.HttpCloseCircleApi

private val Context.dataStore by preferencesDataStore(name = "closecircle_prefs")

/** Manual wiring; switch to Hilt when the dependency graph outgrows this. */
class CloseCircleApp : Application() {
    lateinit var session: Session
        private set
    lateinit var api: CloseCircleApi
        private set
    lateinit var repository: AppRepository
        private set
    lateinit var prefs: UserPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(DataStoreTokenStore(dataStore))
        api = HttpCloseCircleApi(ApiClient(BuildConfig.API_BASE_URL, token = { session.token }))
        repository = AppRepository(api)
        prefs = UserPrefs(dataStore)
    }
}
