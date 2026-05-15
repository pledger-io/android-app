package com.pledgerio.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pledgerio.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = context.userPreferencesDataStore

    private val _displayCurrencyCode = MutableStateFlow(DEFAULT_CURRENCY)
    val displayCurrencyCode: StateFlow<String> = _displayCurrencyCode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        scope.launch {
            dataStore.data.map { prefs ->
                prefs[KEY_DISPLAY_CURRENCY] ?: DEFAULT_CURRENCY
            }.collect { _displayCurrencyCode.value = it }
        }
        scope.launch {
            dataStore.data.map { prefs ->
                ThemeMode.fromStorage(prefs[KEY_THEME_MODE])
            }.collect { _themeMode.value = it }
        }
        companionInstance = this
    }

    suspend fun setDisplayCurrency(code: String) {
        dataStore.edit { it[KEY_DISPLAY_CURRENCY] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storageValue }
    }

    companion object {
        private const val DEFAULT_CURRENCY = "EUR"
        private val KEY_DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        @Volatile
        private var companionInstance: UserPreferences? = null

        fun getInstance(): UserPreferences? = companionInstance

        val defaultDisplayCurrency: String
            get() = companionInstance?.displayCurrencyCode?.value ?: DEFAULT_CURRENCY
    }
}
