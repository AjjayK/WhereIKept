package com.whereikept.app.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "HuggingFaceAuthHelper"

// DataStore for storing access tokens
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "hf_auth")

/**
 * Helper class for HuggingFace OAuth authentication.
 *
 * Handles the OAuth flow for accessing gated HuggingFace models:
 * 1. User clicks "Download Model"
 * 2. Opens browser for HuggingFace login
 * 3. User grants access
 * 4. App receives access token
 * 5. Token is used for downloading model
 *
 * Based on Google AI Edge Gallery's OAuth implementation.
 */
class HuggingFaceAuthHelper(private val context: Context) {

    private val authService = AuthorizationService(context)

    // DataStore keys
    private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")

    // HuggingFace OAuth configuration
    private val authServiceConfig = AuthorizationServiceConfiguration(
        "https://huggingface.co/oauth/authorize".toUri(),
        "https://huggingface.co/oauth/token".toUri()
    )

    // OAuth client ID (you need to register your app at HuggingFace)
    // For testing, we'll use a placeholder - user needs to register their app
    private val clientId = "06a04d91-8e57-4304-b566-52c457c7d43c" 

    // Redirect URI (must match what's registered in HuggingFace)
    private val redirectUri = "com.whereikept.app://oauth/callback".toUri()

    /**
     * Get the authorization request for HuggingFace OAuth
     */
    fun getAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            authServiceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScope("read-repos") // Permission to read repositories (gated models)
            .build()
    }

    /**
     * Get the authorization intent to launch
     */
    fun getAuthorizationIntent(): Intent {
        val authRequest = getAuthorizationRequest()
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle the authorization result and exchange code for token
     */
    suspend fun handleAuthResult(
        result: ActivityResult,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val dataIntent = result.data

        if (dataIntent == null) {
            onError("No data returned from authentication")
            return
        }

        val authResponse = AuthorizationResponse.fromIntent(dataIntent)
        val authException = AuthorizationException.fromIntent(dataIntent)

        if (authException != null) {
            Log.e(TAG, "Authorization failed: ${authException.message}")
            onError(authException.message ?: "Authorization failed")
            return
        }

        if (authResponse == null) {
            onError("No authorization response")
            return
        }

        Log.i(TAG, "Authorization successful, exchanging code for token...")

        // Exchange authorization code for access token
        authService.performTokenRequest(
            authResponse.createTokenExchangeRequest()
        ) { tokenResponse, tokenException ->
            if (tokenException != null) {
                Log.e(TAG, "Token exchange failed: ${tokenException.message}")
                onError(tokenException.message ?: "Token exchange failed")
                return@performTokenRequest
            }

            if (tokenResponse == null) {
                onError("No token response")
                return@performTokenRequest
            }

            val accessToken = tokenResponse.accessToken
            val expiryTime = tokenResponse.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3600000)

            Log.i(TAG, "Token received successfully")

            // Save token to DataStore
            MainScope().launch(Dispatchers.IO) {
                saveToken(accessToken!!, expiryTime)
                withContext(Dispatchers.Main) {
                    onSuccess(accessToken)
                }
            }
        }
    }

    /**
     * Save access token to DataStore
     */
    private suspend fun saveToken(accessToken: String, expiryTime: Long) {
        context.authDataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = accessToken
            preferences[KEY_TOKEN_EXPIRY] = expiryTime
        }
        Log.i(TAG, "Token saved to DataStore")
    }

    /**
     * Get stored access token
     */
    suspend fun getStoredToken(): String? {
        return context.authDataStore.data.map { preferences ->
            val token = preferences[KEY_ACCESS_TOKEN]
            val expiry = preferences[KEY_TOKEN_EXPIRY] ?: 0L

            if (token != null && System.currentTimeMillis() < expiry) {
                token
            } else {
                null
            }
        }.first()
    }

    /**
     * Check if stored token is valid
     */
    suspend fun isTokenValid(): Boolean {
        return getStoredToken() != null
    }

    /**
     * Clear stored token
     */
    suspend fun clearToken() {
        context.authDataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_TOKEN_EXPIRY)
        }
        Log.i(TAG, "Token cleared from DataStore")
    }

    /**
     * Test if a URL is accessible (for checking if model is gated)
     */
    suspend fun testUrlAccess(url: String, accessToken: String? = null): Int {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = true  // Follow redirects
                connection.connectTimeout = 10000  // 10 second timeout
                connection.readTimeout = 10000

                if (accessToken != null) {
                    Log.d(TAG, "Testing URL with access token: ${accessToken.take(10)}...")
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                } else {
                    Log.d(TAG, "Testing URL without access token")
                }

                connection.connect()
                val responseCode = connection.responseCode
                connection.disconnect()

                Log.d(TAG, "URL access test result: HTTP $responseCode for ${url.take(60)}...")
                responseCode
            } catch (e: Exception) {
                Log.e(TAG, "URL access test failed: ${e.message}", e)
                -1
            }
        }
    }

    /**
     * Dispose of auth service
     */
    fun dispose() {
        authService.dispose()
    }
}

/**
 * Enum for token status
 */
enum class TokenStatus {
    NOT_STORED,
    EXPIRED,
    VALID
}
