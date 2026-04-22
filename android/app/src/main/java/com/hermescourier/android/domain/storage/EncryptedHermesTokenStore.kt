
package com.hermescourier.android.domain.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermescourier.android.domain.model.HermesAuthSession
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EncryptedHermesTokenStore(context: Context) : HermesTokenStore {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hermes_courier_tokens",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun save(session: HermesAuthSession) = withContext(Dispatchers.IO) {
        val ok = sharedPreferences.edit().putString(KEY_SESSION, session.toJson().toString()).commit()
        if (!ok) throw IOException("Hermes token store commit failed")
    }

    override suspend fun load(): HermesAuthSession? = withContext(Dispatchers.IO) {
        val json = sharedPreferences.getString(KEY_SESSION, null) ?: return@withContext null
        hermesAuthSessionFromJson(JSONObject(json))
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().remove(KEY_SESSION).commit()
        }
    }

    private companion object {
        private const val KEY_SESSION = "current_session"
    }
}

private fun HermesAuthSession.toJson(): JSONObject = JSONObject()
    .put("sessionId", sessionId)
    .put("accessToken", accessToken)
    .put("refreshToken", refreshToken)
    .put("expiresAt", expiresAt)
    .put("gatewayUrl", gatewayUrl)
    .put("mtlsRequired", mtlsRequired)
    .put("scope", JSONArray(scope))

private fun hermesAuthSessionFromJson(json: JSONObject): HermesAuthSession =
    HermesAuthSession(
        sessionId = json.getString("sessionId"),
        accessToken = json.getString("accessToken"),
        refreshToken = json.getString("refreshToken"),
        expiresAt = json.getString("expiresAt"),
        gatewayUrl = json.getString("gatewayUrl"),
        mtlsRequired = json.optBoolean("mtlsRequired", false),
        scope = json.optJSONArray("scope")?.let { array ->
            buildList {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        } ?: emptyList(),
    )
