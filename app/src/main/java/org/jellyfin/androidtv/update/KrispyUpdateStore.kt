package org.jellyfin.androidtv.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface KrispyUpdateStore {
	var lastAttempt: Long
	var lastSuccessfulCheck: Long
	var cachedUpdate: KrispyApprovedUpdate?
}

class KrispyUpdatePreferences(context: Context) : KrispyUpdateStore {
	private val preferences = context.getSharedPreferences("krispy_updates", Context.MODE_PRIVATE)

	override var lastAttempt: Long
		get() = preferences.getLong("last_attempt", 0)
		set(value) { preferences.edit { putLong("last_attempt", value) } }

	override var lastSuccessfulCheck: Long
		get() = preferences.getLong("last_successful_check", 0)
		set(value) { preferences.edit { putLong("last_successful_check", value) } }

	override var cachedUpdate: KrispyApprovedUpdate?
		get() = preferences.getString("latest_release", null)?.let {
			if (it.length > MAX_UPDATE_METADATA_BYTES) return null
			runCatching { krispyUpdateJson.decodeFromString<KrispyApprovedUpdate>(it) }.getOrNull()
		}
		set(value) {
			preferences.edit {
				if (value == null) remove("latest_release")
				else putString("latest_release", krispyUpdateJson.encodeToString(value))
			}
		}
}
