package org.jellyfin.androidtv.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class KrispyApkDownloader(
	private val http: KrispyUpdateHttp,
	private val verifier: KrispyApkVerifier,
	private val directory: File,
) {
	suspend fun prepare(update: KrispyApprovedUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
		if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create update storage")
		removeStaleFiles()
		val partial = File.createTempFile("download-", ".part", directory)
		try {
			http.download(update, partial, onProgress)
			currentCoroutineContext().ensureActive()
			verifier.verify(partial, update.metadata)
			currentCoroutineContext().ensureActive()
			val apk = File(directory, "krispy-" + update.metadata.versionCode + "-" + UUID.randomUUID() + ".apk")
			if (!partial.renameTo(apk)) throw IOException("Cannot finish update download")
			apk
		} finally {
			partial.delete()
		}
	}

	/** Recheck private storage before every handoff, including after returning from Android settings. */
	suspend fun verify(file: File, update: KrispyApprovedUpdate) = withContext(Dispatchers.IO) {
		require(file.canonicalFile.parentFile == directory.canonicalFile && file.extension == "apk")
		if (file.length() != update.size) throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
		val digest = MessageDigest.getInstance("SHA-256")
		file.inputStream().use { input ->
			val buffer = ByteArray(VERIFY_BUFFER_SIZE)
			while (true) {
				currentCoroutineContext().ensureActive()
				val count = input.read(buffer)
				if (count < 0) break
				digest.update(buffer, 0, count)
			}
		}
		if (!digest.digest().toUpdateHex().equals(update.metadata.sha256, ignoreCase = true)) {
			throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
		}
		verifier.verify(file, update.metadata)
	}

	private fun removeStaleFiles() {
		val cutoff = System.currentTimeMillis() - STALE_FILE_AGE
		directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff && it.extension in setOf("apk", "part") }
			?.forEach { it.delete() }
	}

	private companion object {
		const val VERIFY_BUFFER_SIZE = 16 * 1024
		val STALE_FILE_AGE = 48.hours.inWholeMilliseconds
	}
}
