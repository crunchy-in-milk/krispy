package org.jellyfin.androidtv.update

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.androidtv.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val GITHUB_HTTPS_PORT = 443

/** Independent client: no Jellyfin server token, cookies or GitHub credential. */
class KrispyUpdateHttp {
	private val client = OkHttpClient.Builder()
		.connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.followRedirects(false)
		.followSslRedirects(false)
		.build()

	suspend fun read(url: String, maxBytes: Long, allowMissing: Boolean = false): ByteArray? = followRedirects(url) { response, context ->
		if (allowMissing && response.code == NOT_FOUND) null
		else {
			requireSuccess(response)
			readUpdateBytes(requireNotNull(response.body).byteStream(), maxBytes, context)
		}
	}

	suspend fun download(update: KrispyApprovedUpdate, destination: File, onProgress: (Int) -> Unit) {
		followRedirects(update.downloadUrl) { response, context ->
			requireSuccess(response)
			val body = requireNotNull(response.body)
			val length = body.contentLength()
			if (length >= 0 && length != update.size) throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
			destination.outputStream().use { output ->
				body.byteStream().copyVerifiedUpdate(output, update.size, update.metadata.sha256, context, onProgress)
			}
		}
	}

	private suspend fun <T> followRedirects(url: String, reader: (Response, CoroutineContext) -> T): T {
		var currentUrl = url
		repeat(MAX_REDIRECTS) {
			when (val hop = execute(currentUrl, reader)) {
				is Hop.Body -> return hop.value
				is Hop.Redirect -> {
					val target = URI.create(currentUrl).resolve(hop.location).toString()
					require(isKrispyAssetRedirect(target)) { "Untrusted release asset redirect" }
					currentUrl = target
				}
			}
		}
		throw IOException("Too many release asset redirects")
	}

	private suspend fun <T> execute(url: String, reader: (Response, CoroutineContext) -> T): Hop<T> =
		suspendCancellableCoroutine { continuation ->
			val request = Request.Builder().url(url)
				.header("User-Agent", "Krispy/" + BuildConfig.VERSION_NAME)
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2026-03-10")
				.build()
			val call = client.newCall(request)
			continuation.invokeOnCancellation { call.cancel() }
			call.enqueue(object : Callback {
				override fun onFailure(call: Call, e: IOException) {
					if (continuation.isActive) continuation.resumeWithException(e)
				}

				override fun onResponse(call: Call, response: Response) {
					runCatching {
						response.use {
							if (response.code in REDIRECT_CODES) Hop.Redirect(requireNotNull(response.header("Location")))
							else Hop.Body(reader(response, continuation.context))
						}
					}.fold(
						onSuccess = { if (continuation.isActive) continuation.resume(it) },
						onFailure = { if (continuation.isActive) continuation.resumeWithException(it) },
					)
				}
			})
		}

	private fun requireSuccess(response: Response) {
		if (response.code != OK) throw IOException("GitHub release request did not succeed")
	}

	private sealed interface Hop<out T> {
		data class Body<T>(val value: T) : Hop<T>
		data class Redirect(val location: String) : Hop<Nothing>
	}

	private companion object {
		const val CONNECT_TIMEOUT_SECONDS = 15L
		const val READ_TIMEOUT_SECONDS = 30L
		const val MAX_REDIRECTS = 5
		const val OK = 200
		const val NOT_FOUND = 404
		val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
	}
}

/** Redirects may reach GitHub's asset CDNs, never a different repository or arbitrary host. */
internal fun isKrispyAssetRedirect(url: String): Boolean = runCatching {
	val uri = URI(url)
	uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null &&
		uri.port in setOf(-1, GITHUB_HTTPS_PORT) && uri.host in setOf(
			"release-assets.githubusercontent.com",
			"objects.githubusercontent.com",
			"github-releases.githubusercontent.com",
		)
}.getOrDefault(false)
