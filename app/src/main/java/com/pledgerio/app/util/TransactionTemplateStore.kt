package com.pledgerio.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pledgerio.app.domain.model.TransactionTemplate
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.transactionTemplatesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "transaction_templates",
)

@Singleton
class TransactionTemplateStore @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi,
) {
    private val dataStore = context.transactionTemplatesDataStore
    private val listType = Types.newParameterizedType(List::class.java, TransactionTemplate::class.java)
    private val adapter = moshi.adapter<List<TransactionTemplate>>(listType)

    val templates: Flow<List<TransactionTemplate>> = dataStore.data.map { prefs ->
        val json = prefs[KEY_TEMPLATES] ?: "[]"
        runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()
    }

    suspend fun save(template: TransactionTemplate) {
        dataStore.edit { prefs ->
            val current = parse(prefs[KEY_TEMPLATES])
            val withoutDuplicate = current.filterNot { it.id == template.id }
            val updated = (listOf(template) + withoutDuplicate).take(MAX_TEMPLATES)
            prefs[KEY_TEMPLATES] = adapter.toJson(updated)
        }
    }

    suspend fun saveFromForm(
        name: String,
        description: String,
        amount: String,
        type: String,
        currency: String,
        sourceAccountId: Long?,
        sourceAccountName: String,
        targetAccountId: Long?,
        targetAccountName: String,
        tags: List<String>,
    ): TransactionTemplate {
        val template = TransactionTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            amount = amount.trim(),
            type = type,
            currency = currency,
            sourceAccountId = sourceAccountId,
            sourceAccountName = sourceAccountName,
            targetAccountId = targetAccountId,
            targetAccountName = targetAccountName,
            tags = tags,
        )
        save(template)
        return template
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val updated = parse(prefs[KEY_TEMPLATES]).filterNot { it.id == id }
            prefs[KEY_TEMPLATES] = adapter.toJson(updated)
        }
    }

    private fun parse(json: String?): List<TransactionTemplate> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()
    }

    companion object {
        private val KEY_TEMPLATES = stringPreferencesKey("templates_json")
        const val MAX_TEMPLATES = 20
    }
}
