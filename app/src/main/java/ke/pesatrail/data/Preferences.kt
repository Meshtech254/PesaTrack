package ke.pesatrail.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("privacy")
@Singleton class Preferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.preferences
    val settings = store.data.map { p -> Settings(p[booleanPreferencesKey("onboarded")] ?: false,p[booleanPreferencesKey("biometric")] ?: false,p[stringPreferencesKey("pinHash")] ?: "",p[stringPreferencesKey("salt")] ?: "",p[stringPreferencesKey("theme")] ?: "System",p[stringPreferencesKey("providers")] ?: "",p[booleanPreferencesKey("reconcile")] ?: false) }
    suspend fun bool(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun text(key: String, value: String) { store.edit { it[stringPreferencesKey(key)] = value } }
}
data class Settings(val onboarded: Boolean, val biometric: Boolean, val pinHash: String, val salt: String, val theme: String, val providers: String, val reconcile: Boolean)
