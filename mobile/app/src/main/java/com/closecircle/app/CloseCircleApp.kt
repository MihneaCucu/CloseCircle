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
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

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
        MapLibre.getInstance(this)
        // OpenStreetMap's tile policy asks apps to identify themselves instead of using a library default.
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", "CloseCircle/${BuildConfig.VERSION_NAME} (Android)").build()) }
                .build(),
        )
        session = Session(DataStoreTokenStore(dataStore))
        api = HttpCloseCircleApi(ApiClient(BuildConfig.API_BASE_URL, token = { session.token }))
        repository = AppRepository(api)
        prefs = UserPrefs(dataStore)
    }
}
