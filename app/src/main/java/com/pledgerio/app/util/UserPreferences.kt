package com.pledgerio.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pledgerio.app.domain.model.AppLocale
import com.pledgerio.app.domain.model.FinanceExperienceMode
import com.pledgerio.app.domain.model.ThemeMode
import com.pledgerio.app.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _financeExperienceMode = MutableStateFlow(FinanceExperienceMode.GUIDED)
    val financeExperienceMode: StateFlow<FinanceExperienceMode> = _financeExperienceMode.asStateFlow()

    private val _appLocale = MutableStateFlow(AppLocale.SYSTEM)
    val appLocale: StateFlow<AppLocale> = _appLocale.asStateFlow()

    private val _budgetAlertsEnabled = MutableStateFlow(BudgetAlertLogic.DEFAULT_ENABLED)
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()

    private val _budgetAlertThresholdPercent =
        MutableStateFlow(BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT)
    val budgetAlertThresholdPercent: StateFlow<Int> = _budgetAlertThresholdPercent.asStateFlow()

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
        scope.launch {
            dataStore.data.map { prefs ->
                FinanceExperienceMode.fromStorage(prefs[KEY_FINANCE_EXPERIENCE_MODE])
            }.collect { _financeExperienceMode.value = it }
        }
        scope.launch {
            dataStore.data.map { prefs ->
                AppLocale.fromStorage(prefs[KEY_APP_LOCALE])
            }.collect { _appLocale.value = it }
        }
        scope.launch {
            dataStore.data.map { prefs ->
                prefs[KEY_BUDGET_ALERTS_ENABLED] ?: BudgetAlertLogic.DEFAULT_ENABLED
            }.collect { _budgetAlertsEnabled.value = it }
        }
        scope.launch {
            dataStore.data.map { prefs ->
                BudgetAlertLogic.normalizeThresholdPercent(
                    prefs[KEY_BUDGET_ALERT_THRESHOLD_PERCENT]
                        ?: BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT,
                )
            }.collect { _budgetAlertThresholdPercent.value = it }
        }
        companionInstance = this
    }

    suspend fun appLocaleOnce(): AppLocale =
        dataStore.data.map { prefs -> AppLocale.fromStorage(prefs[KEY_APP_LOCALE]) }.first()

    suspend fun setDisplayCurrency(code: String) {
        dataStore.edit { it[KEY_DISPLAY_CURRENCY] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storageValue }
    }

    suspend fun setFinanceExperienceMode(mode: FinanceExperienceMode) {
        dataStore.edit { it[KEY_FINANCE_EXPERIENCE_MODE] = mode.storageValue }
    }

    suspend fun setAppLocale(locale: AppLocale) {
        dataStore.edit { it[KEY_APP_LOCALE] = locale.storageValue }
    }

    suspend fun getBudgetAlertsEnabled(): Boolean =
        dataStore.data.map { prefs ->
            prefs[KEY_BUDGET_ALERTS_ENABLED] ?: BudgetAlertLogic.DEFAULT_ENABLED
        }.first()

    suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BUDGET_ALERTS_ENABLED] = enabled }
    }

    suspend fun getBudgetAlertThresholdPercent(): Int =
        dataStore.data.map { prefs ->
            BudgetAlertLogic.normalizeThresholdPercent(
                prefs[KEY_BUDGET_ALERT_THRESHOLD_PERCENT]
                    ?: BudgetAlertLogic.DEFAULT_THRESHOLD_PERCENT,
            )
        }.first()

    suspend fun setBudgetAlertThresholdPercent(percent: Int) {
        val normalized = BudgetAlertLogic.normalizeThresholdPercent(percent)
        dataStore.edit { it[KEY_BUDGET_ALERT_THRESHOLD_PERCENT] = normalized }
    }

    /**
     * Returns `true` if [fingerprint] is new (should notify) and stores it;
     * `false` if unchanged (suppress).
     */
    suspend fun consumeBudgetAlertFingerprint(fingerprint: String): Boolean {
        var shouldNotify = false
        dataStore.edit { prefs ->
            val previous = prefs[KEY_BUDGET_ALERT_LAST_FINGERPRINT]
            if (BudgetAlertLogic.isFingerprintNew(previous, fingerprint)) {
                prefs[KEY_BUDGET_ALERT_LAST_FINGERPRINT] = fingerprint
                shouldNotify = true
            }
        }
        return shouldNotify
    }

    /** Clears the dedup token so a later over-threshold set can alert again. */
    suspend fun clearBudgetAlertFingerprint() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BUDGET_ALERT_LAST_FINGERPRINT)
        }
    }

    suspend fun getLastTransactionType(): TransactionType? {
        val stored = dataStore.data.map { prefs ->
            prefs[KEY_LAST_TRANSACTION_TYPE]
        }.first()
        return stored?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
    }

    suspend fun setLastTransactionType(type: TransactionType) {
        dataStore.edit { it[KEY_LAST_TRANSACTION_TYPE] = type.name }
    }

    /**
     * Clears preferences tied to the previous signed-in user
     * (keeps theme, experience mode, locale, and budget alert prefs).
     */
    suspend fun clearSessionData() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DISPLAY_CURRENCY)
            prefs.remove(KEY_LAST_TRANSACTION_TYPE)
        }
        _displayCurrencyCode.value = DEFAULT_CURRENCY
    }

    companion object {
        private const val DEFAULT_CURRENCY = "EUR"
        private val KEY_DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_FINANCE_EXPERIENCE_MODE = stringPreferencesKey("finance_experience_mode")
        private val KEY_APP_LOCALE = stringPreferencesKey("app_locale")
        private val KEY_LAST_TRANSACTION_TYPE = stringPreferencesKey("last_transaction_type")
        private val KEY_BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        private val KEY_BUDGET_ALERT_THRESHOLD_PERCENT =
            intPreferencesKey("budget_alert_threshold_percent")
        private val KEY_BUDGET_ALERT_LAST_FINGERPRINT =
            stringPreferencesKey("budget_alert_last_fingerprint")

        @Volatile
        private var companionInstance: UserPreferences? = null

        fun getInstance(): UserPreferences? = companionInstance

        val defaultDisplayCurrency: String
            get() = companionInstance?.displayCurrencyCode?.value ?: DEFAULT_CURRENCY
    }
}
