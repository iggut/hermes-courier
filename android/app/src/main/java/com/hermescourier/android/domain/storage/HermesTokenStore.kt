
package com.hermescourier.android.domain.storage

import com.hermescourier.android.domain.model.HermesAuthSession

interface HermesTokenStore {
    suspend fun save(session: HermesAuthSession)
    suspend fun load(): HermesAuthSession?
    suspend fun clear()
}
