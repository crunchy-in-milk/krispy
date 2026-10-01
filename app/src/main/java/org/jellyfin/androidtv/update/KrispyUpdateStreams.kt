package org.jellyfin.androidtv.update

import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext

private const val UPDATE_BUFFER_SIZE = 16 * 1024
private const val COMPLETE_PROGRESS = 100

internal fun readUpdateBytes(input: InputStream, maxBytes: Long, context: CoroutineContext): ByteArray {
	val output = ByteArrayOutputStream()
	val buffer = ByteArray(UPDATE_BUFFER_SIZE)
	var total = 0L
	while (true) {
		context.ensureActive()
		val count = input.read(buffer)
		if (count < 0) break
		total += count
		require(total <= maxBytes) { "Update metadata exceeds its size limit" }
		output.write(buffer, 0, count)
	}
	context.ensureActive()
	return output.toByteArray()
}

/** A completed copy is not usable until its exact length and digest have both matched. */
internal fun InputStream.copyVerifiedUpdate(
	output: OutputStream,
	expectedSize: Long,
	expectedSha256: String,
	context: CoroutineContext,
	onProgress: (Int) -> Unit = {},
) {
	require(expectedSize in 1..MAX_UPDATE_APK_BYTES)
	val digest = MessageDigest.getInstance("SHA-256")
	val buffer = ByteArray(UPDATE_BUFFER_SIZE)
	var total = 0L
	var lastProgress = -1
	while (true) {
		context.ensureActive()
		val count = read(buffer)
		if (count < 0) break
		total += count
		if (total > expectedSize) throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
		output.write(buffer, 0, count)
		digest.update(buffer, 0, count)
		val progress = (total * COMPLETE_PROGRESS / expectedSize).toInt()
		if (progress != lastProgress) {
			lastProgress = progress
			onProgress(progress)
		}
	}
	context.ensureActive()
	if (total != expectedSize || !digest.digest().toUpdateHex().equals(expectedSha256, ignoreCase = true)) {
		throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
	}
}

internal fun ByteArray.toUpdateHex() = joinToString("") { "%02x".format(it) }
