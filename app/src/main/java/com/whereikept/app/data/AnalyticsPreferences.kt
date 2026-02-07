package com.whereikept.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore for managing analytics consent preferences
 * Privacy-first: Analytics disabled by default
 */
class AnalyticsPreferencesManager(private val context: Context) {

    companion object {
        private val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "analytics_preferences"
        )

        // Preference keys
        private val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        private val CONSENT_DIALOG_SHOWN = booleanPreferencesKey("consent_dialog_shown")
        private val LOCAL_METRICS_ENABLED = booleanPreferencesKey("local_metrics_enabled")
    }

    /**
     * Flow of analytics enabled state
     * Default: false (opt-in required)
     */
    val analyticsEnabledFlow: Flow<Boolean> = context.analyticsDataStore.data
        .map { preferences ->
            preferences[ANALYTICS_ENABLED] ?: false
        }

    /**
     * Flow of consent dialog shown state
     */
    val consentDialogShownFlow: Flow<Boolean> = context.analyticsDataStore.data
        .map { preferences ->
            preferences[CONSENT_DIALOG_SHOWN] ?: false
        }

    /**
     * Flow of local metrics enabled state
     * Local metrics can be enabled separately from cloud analytics
     * Default: true (always collect locally, but don't send to cloud)
     */
    val localMetricsEnabledFlow: Flow<Boolean> = context.analyticsDataStore.data
        .map { preferences ->
            preferences[LOCAL_METRICS_ENABLED] ?: true
        }

    /**
     * Check if analytics is enabled
     */
    suspend fun isAnalyticsEnabled(): Boolean {
        return context.analyticsDataStore.data
            .map { preferences -> preferences[ANALYTICS_ENABLED] ?: false }
            .first()
    }

    /**
     * Check if local metrics collection is enabled
     */
    suspend fun isLocalMetricsEnabled(): Boolean {
        return context.analyticsDataStore.data
            .map { preferences -> preferences[LOCAL_METRICS_ENABLED] ?: true }
            .first()
    }

    /**
     * Check if consent dialog has been shown
     */
    suspend fun hasShownConsentDialog(): Boolean {
        return context.analyticsDataStore.data
            .map { preferences -> preferences[CONSENT_DIALOG_SHOWN] ?: false }
            .first()
    }

    /**
     * Enable cloud analytics (user opted in)
     */
    suspend fun enableAnalytics() {
        context.analyticsDataStore.edit { preferences ->
            preferences[ANALYTICS_ENABLED] = true
            preferences[LOCAL_METRICS_ENABLED] = true
        }
    }

    /**
     * Disable cloud analytics (user opted out)
     * Local metrics remain enabled by default
     */
    suspend fun disableAnalytics() {
        context.analyticsDataStore.edit { preferences ->
            preferences[ANALYTICS_ENABLED] = false
            // Keep local metrics enabled
            preferences[LOCAL_METRICS_ENABLED] = true
        }
    }

    /**
     * Mark consent dialog as shown
     */
    suspend fun markConsentDialogShown() {
        context.analyticsDataStore.edit { preferences ->
            preferences[CONSENT_DIALOG_SHOWN] = true
        }
    }

    /**
     * Enable local metrics collection
     */
    suspend fun enableLocalMetrics() {
        context.analyticsDataStore.edit { preferences ->
            preferences[LOCAL_METRICS_ENABLED] = true
        }
    }

    /**
     * Disable local metrics collection
     */
    suspend fun disableLocalMetrics() {
        context.analyticsDataStore.edit { preferences ->
            preferences[LOCAL_METRICS_ENABLED] = false
        }
    }

    /**
     * Reset all analytics preferences (for testing or user-requested data deletion)
     */
    suspend fun resetAllPreferences() {
        context.analyticsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Get all current preferences as a data class (for debugging/settings display)
     */
    data class AnalyticsPreferencesState(
        val analyticsEnabled: Boolean,
        val localMetricsEnabled: Boolean,
        val consentDialogShown: Boolean
    )

    val preferencesStateFlow: Flow<AnalyticsPreferencesState> = context.analyticsDataStore.data
        .map { preferences ->
            AnalyticsPreferencesState(
                analyticsEnabled = preferences[ANALYTICS_ENABLED] ?: false,
                localMetricsEnabled = preferences[LOCAL_METRICS_ENABLED] ?: true,
                consentDialogShown = preferences[CONSENT_DIALOG_SHOWN] ?: false
            )
        }
}
