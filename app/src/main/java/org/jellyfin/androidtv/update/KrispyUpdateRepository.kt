package org.jellyfin.androidtv.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.hours

enum class KrispyUpdateMessage {
	UP_TO_DATE, NO_RELEASE, CHECK_FAILED, INVALID_RELEASE, DOWNLOAD_FAILED, INVALID_APK, INSTALL_UNAVAILABLE,
}

class KrispyUpdateException(val reason: KrispyUpdateMessage) : IOException(reason.name)

data class KrispyUpdateState(
	val available: KrispyApprovedUpdate? = null,
	val checking: Boolean = false,
	val downloading: Boolean = false,
	val progress: Int = 0,
	val readyApk: File? = null,
	val message: KrispyUpdateMessage? = null,
) {
	val busy get() = checking || downloading
}

/** Shared app state; work belongs to the calling lifecycle and never blocks startup. */
class KrispyUpdateRepository(
	private val source: KrispyReleaseClient,
	private val policy: KrispyUpdatePolicy,
	private val store: KrispyUpdateStore,
	private val downloader: KrispyApkDownloader,
	private val installedVersionCode: Long,
	private val clock: () -> Long = System::currentTimeMillis,
) {
	private val mutex = Mutex()
	private val _state = MutableStateFlow(KrispyUpdateState(available = cachedUpdate()))
	val state = _state.asStateFlow()

	private fun cachedUpdate() = runCatching {
		store.cachedUpdate?.let(policy::validateCached)?.takeIf { it.metadata.versionCode > installedVersionCode }
	}.getOrNull()

	suspend fun check(force: Boolean = false) {
		if (!mutex.tryLock()) return
		try {
			val now = clock()
			if (!shouldCheck(now, force)) return
			store.lastAttempt = now
			_state.value = state.value.copy(checking = true, message = null)
			applyRelease(source.latest(), now)
		} catch (error: CancellationException) {
			throw error
		} catch (_: IOException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.CHECK_FAILED)
		} catch (_: SerializationException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.INVALID_RELEASE)
		} catch (_: IllegalArgumentException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.INVALID_RELEASE)
		} finally {
			_state.value = state.value.copy(checking = false)
			mutex.unlock()
		}
	}

	private fun shouldCheck(now: Long, force: Boolean): Boolean {
		if (force) return true
		val lastAttempt = store.lastAttempt
		if (lastAttempt <= 0 || now < lastAttempt) return true
		return now - lastAttempt >= CHECK_INTERVAL
	}

	private fun applyRelease(release: KrispyApprovedUpdate?, now: Long) {
		val validated = release?.let(policy::validateCached)
		store.cachedUpdate = validated
		store.lastSuccessfulCheck = now
		val available = validated?.takeIf { it.metadata.versionCode > installedVersionCode }
		_state.value = state.value.copy(
			available = available,
			readyApk = state.value.readyApk.takeIf { available != null && available == state.value.available },
			message = when {
				available != null -> null
				validated == null -> KrispyUpdateMessage.NO_RELEASE
				else -> KrispyUpdateMessage.UP_TO_DATE
			},
		)
	}

	suspend fun download() {
		if (!mutex.tryLock()) return
		try {
			val update = state.value.available ?: return
			_state.value = state.value.copy(downloading = true, progress = 0, readyApk = null, message = null)
			val apk = downloader.prepare(update) { progress -> _state.value = state.value.copy(progress = progress) }
			_state.value = state.value.copy(readyApk = apk)
		} catch (error: CancellationException) {
			throw error
		} catch (error: KrispyUpdateException) {
			_state.value = state.value.copy(message = error.reason)
		} catch (_: IOException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.DOWNLOAD_FAILED)
		} catch (_: IllegalArgumentException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.INVALID_APK)
		} catch (_: SecurityException) {
			_state.value = state.value.copy(message = KrispyUpdateMessage.INVALID_APK)
		} finally {
			_state.value = state.value.copy(downloading = false)
			mutex.unlock()
		}
	}

	suspend fun verifiedApk(): File? {
		if (!mutex.tryLock()) return null
		try {
			val update = state.value.available ?: return null
			val file = state.value.readyApk ?: return null
			downloader.verify(file, update)
			return file
		} catch (_: IOException) {
			_state.value = state.value.copy(readyApk = null, message = KrispyUpdateMessage.INVALID_APK)
			return null
		} catch (_: IllegalArgumentException) {
			_state.value = state.value.copy(readyApk = null, message = KrispyUpdateMessage.INVALID_APK)
			return null
		} finally {
			mutex.unlock()
		}
	}

	fun installerUnavailable() {
		_state.value = state.value.copy(message = KrispyUpdateMessage.INSTALL_UNAVAILABLE)
	}

	private companion object {
		val CHECK_INTERVAL = 12.hours.inWholeMilliseconds
	}
}
