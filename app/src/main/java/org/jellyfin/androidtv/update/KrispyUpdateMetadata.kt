package org.jellyfin.androidtv.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder

internal val krispyUpdateJson = Json { ignoreUnknownKeys = true }
internal const val KRISPY_UPDATE_DIRECTORY = "krispy-updates"
internal const val KRISPY_UPDATE_METADATA = "krispy-update.json"
internal const val MAX_UPDATE_METADATA_BYTES = 256 * 1024L
internal const val MAX_UPDATE_APK_BYTES = 250 * 1024 * 1024L

@Serializable
data class KrispyUpdateMetadata(
	val versionCode: Long,
	val versionName: String,
	val apkAsset: String,
	val sha256: String,
)

@Serializable
data class KrispyReleaseAsset(
	val name: String,
	@SerialName("browser_download_url") val downloadUrl: String,
	val size: Long,
	val state: String,
	val digest: String? = null,
)

@Serializable
data class KrispyRelease(
	@SerialName("tag_name") val tag: String,
	val draft: Boolean,
	val prerelease: Boolean,
	val assets: List<KrispyReleaseAsset>,
)

@Serializable
data class KrispyApprovedUpdate(
	val metadata: KrispyUpdateMetadata,
	val tag: String,
	val downloadUrl: String,
	val size: Long,
)

/** Accept assets only from the configured repository and the selected full release. */
class KrispyUpdatePolicy(val owner: String, val repository: String) {
	val latestReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest"

	init {
		require(OWNER.matches(owner) && REPOSITORY.matches(repository))
	}

	fun metadataAsset(release: KrispyRelease): KrispyReleaseAsset {
		require(!release.draft && !release.prerelease)
		val asset = requireNotNull(release.assets.singleOrNull { it.name == KRISPY_UPDATE_METADATA })
		validateAsset(release.tag, asset, MAX_UPDATE_METADATA_BYTES)
		return asset
	}

	fun approve(release: KrispyRelease, metadata: KrispyUpdateMetadata): KrispyApprovedUpdate {
		require(!release.draft && !release.prerelease)
		validateMetadata(metadata)
		val asset = requireNotNull(release.assets.singleOrNull { it.name == metadata.apkAsset })
		validateAsset(release.tag, asset, MAX_UPDATE_APK_BYTES)
		asset.digest?.let { require(it.equals("sha256:" + metadata.sha256, ignoreCase = true)) }
		return KrispyApprovedUpdate(metadata, release.tag, asset.downloadUrl, asset.size)
	}

	fun validateCached(update: KrispyApprovedUpdate): KrispyApprovedUpdate {
		validateMetadata(update.metadata)
		validateAsset(
			update.tag,
			KrispyReleaseAsset(update.metadata.apkAsset, update.downloadUrl, update.size, "uploaded"),
			MAX_UPDATE_APK_BYTES,
		)
		return update
	}

	private fun validateMetadata(metadata: KrispyUpdateMetadata) {
		require(metadata.versionCode in 1..Int.MAX_VALUE.toLong())
		require(metadata.versionName.isNotBlank() && metadata.versionName.length <= MAX_VERSION_NAME_LENGTH)
		require(APK_NAME.matches(metadata.apkAsset) && metadata.apkAsset.length <= MAX_ASSET_NAME_LENGTH)
		require(SHA256.matches(metadata.sha256))
	}

	private fun validateAsset(tag: String, asset: KrispyReleaseAsset, maxSize: Long) {
		require(tag.isNotBlank() && tag.length <= MAX_TAG_LENGTH)
		require(asset.state == "uploaded" && asset.size in 1..maxSize)
		val uri = URI.create(asset.downloadUrl)
		val expectedPath = "/" + owner + "/" + repository + "/releases/download/" + encode(tag) + "/" + encode(asset.name)
		require(uri.scheme == "https" && uri.host == "github.com" && uri.port in setOf(-1, HTTPS_PORT))
		require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
		require(uri.rawPath == expectedPath)
	}

	private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

	private companion object {
		const val HTTPS_PORT = 443
		const val MAX_VERSION_NAME_LENGTH = 100
		const val MAX_ASSET_NAME_LENGTH = 200
		const val MAX_TAG_LENGTH = 200
		val OWNER = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
		val REPOSITORY = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
		val APK_NAME = Regex("""krispy-[A-Za-z0-9][A-Za-z0-9._-]*\.apk""")
		val SHA256 = Regex("[a-fA-F0-9]{64}")
	}
}
