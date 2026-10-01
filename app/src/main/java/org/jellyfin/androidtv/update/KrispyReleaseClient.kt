package org.jellyfin.androidtv.update

import kotlinx.serialization.decodeFromString
import java.security.MessageDigest

class KrispyReleaseClient(
	private val http: KrispyUpdateHttp,
	private val policy: KrispyUpdatePolicy,
) {
	suspend fun latest(): KrispyApprovedUpdate? {
		val response = http.read(policy.latestReleaseUrl, MAX_RELEASE_RESPONSE_BYTES, allowMissing = true) ?: return null
		val release = krispyUpdateJson.decodeFromString<KrispyRelease>(response.toString(Charsets.UTF_8))
		if (release.draft || release.prerelease) return null
		val asset = policy.metadataAsset(release)
		val bytes = requireNotNull(http.read(asset.downloadUrl, MAX_UPDATE_METADATA_BYTES))
		require(bytes.size.toLong() == asset.size)
		asset.digest?.let {
			val digest = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).toUpdateHex()
			require(digest.equals(it, ignoreCase = true))
		}
		val metadata = krispyUpdateJson.decodeFromString<KrispyUpdateMetadata>(bytes.toString(Charsets.UTF_8))
		return policy.approve(release, metadata)
	}

	private companion object {
		const val MAX_RELEASE_RESPONSE_BYTES = 1024 * 1024L
	}
}
