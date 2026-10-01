package org.jellyfin.androidtv.update

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

internal val updateTestPolicy = KrispyUpdatePolicy("crunchy-in-milk", "krispy")
internal val updateTestMetadata = KrispyUpdateMetadata(10000, "0.1.0", "krispy-v0.1.0.apk", "a".repeat(64))
internal fun updateTestAsset(name: String = updateTestMetadata.apkAsset) = KrispyReleaseAsset(
	name, "https://github.com/crunchy-in-milk/krispy/releases/download/v0.1.0/" + name, 128, "uploaded",
)
internal fun updateTestRelease() = KrispyRelease(
	"v0.1.0", false, false,
	listOf(updateTestAsset(), updateTestAsset(KRISPY_UPDATE_METADATA)),
)
internal fun updateTestApproved() = updateTestPolicy.approve(updateTestRelease(), updateTestMetadata)

class KrispyUpdatePolicyTests : FunSpec({
	test("accept the exact metadata APK asset on the configured release") {
		val update = updateTestApproved()
		update.downloadUrl.shouldBe(updateTestAsset().downloadUrl)
		update.metadata.versionCode.shouldBe(10000L)
		updateTestPolicy.validateCached(update).shouldBe(update)
	}
	test("metadata is selected by its exact name and duplicate metadata is rejected") {
		val release = updateTestRelease()
		updateTestPolicy.metadataAsset(release).name.shouldBe(KRISPY_UPDATE_METADATA)
		shouldThrow<IllegalArgumentException> {
			updateTestPolicy.metadataAsset(release.copy(assets = release.assets + updateTestAsset(KRISPY_UPDATE_METADATA)))
		}
	}
	test("missing and duplicate APK assets are rejected") {
		val release = updateTestRelease()
		shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(release.copy(assets = emptyList()), updateTestMetadata) }
		shouldThrow<IllegalArgumentException> {
			updateTestPolicy.approve(release.copy(assets = release.assets + updateTestAsset()), updateTestMetadata)
		}
	}
	test("draft and prerelease assets cannot be approved") {
		shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(updateTestRelease().copy(draft = true), updateTestMetadata) }
		shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(updateTestRelease().copy(prerelease = true), updateTestMetadata) }
	}
	listOf(
		"http://github.com/crunchy-in-milk/krispy/releases/download/v0.1.0/",
		"https://github.com.evil.example/crunchy-in-milk/krispy/releases/download/v0.1.0/",
		"https://github.com/other-owner/krispy/releases/download/v0.1.0/",
		"https://github.com/crunchy-in-milk/other-repo/releases/download/v0.1.0/",
		"https://user@github.com/crunchy-in-milk/krispy/releases/download/v0.1.0/",
		"https://github.com/crunchy-in-milk/krispy/releases/download/other-tag/",
	).forEach { prefix ->
		test("reject an untrusted asset URL: " + prefix) {
			val asset = updateTestAsset().copy(downloadUrl = prefix + updateTestMetadata.apkAsset)
			shouldThrow<IllegalArgumentException> {
				updateTestPolicy.approve(updateTestRelease().copy(assets = listOf(asset)), updateTestMetadata)
			}
		}
	}
	test("URL query injection and malformed cached URLs are rejected") {
		shouldThrow<IllegalArgumentException> {
			updateTestPolicy.validateCached(updateTestApproved().copy(downloadUrl = updateTestAsset().downloadUrl + "?redirect=https://example.org"))
		}
		shouldThrow<IllegalArgumentException> {
			updateTestPolicy.validateCached(updateTestApproved().copy(downloadUrl = "https://github.com/[bad"))
		}
	}
	test("APK names cannot contain a path or target a different product") {
		listOf("../krispy.apk", "jellyfin.apk", "krispy-test.apk/extra").forEach { name ->
			shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(updateTestRelease(), updateTestMetadata.copy(apkAsset = name)) }
		}
	}
	test("version codes must fit Android and checksums must be SHA-256") {
		listOf(0L, -1L, Int.MAX_VALUE.toLong() + 1).forEach { code ->
			shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(updateTestRelease(), updateTestMetadata.copy(versionCode = code)) }
		}
		shouldThrow<IllegalArgumentException> { updateTestPolicy.approve(updateTestRelease(), updateTestMetadata.copy(sha256 = "missing")) }
	}
	test("GitHub's optional APK digest must agree with release metadata") {
		val valid = updateTestAsset().copy(digest = "sha256:" + updateTestMetadata.sha256.uppercase())
		updateTestPolicy.approve(updateTestRelease().copy(assets = listOf(valid)), updateTestMetadata).size.shouldBe(128L)
		shouldThrow<IllegalArgumentException> {
			updateTestPolicy.approve(updateTestRelease().copy(assets = listOf(valid.copy(digest = "sha256:" + "b".repeat(64)))), updateTestMetadata)
		}
	}
	test("pending, empty and oversized assets cannot be installed") {
		listOf(
			updateTestAsset().copy(state = "new"),
			updateTestAsset().copy(size = 0),
			updateTestAsset().copy(size = MAX_UPDATE_APK_BYTES + 1),
		).forEach { asset ->
			shouldThrow<IllegalArgumentException> {
				updateTestPolicy.approve(updateTestRelease().copy(assets = listOf(asset)), updateTestMetadata)
			}
		}
	}
	test("redirects accept only GitHub asset CDN HTTPS endpoints") {
		isKrispyAssetRedirect("https://release-assets.githubusercontent.com/asset?sig=example").shouldBe(true)
		listOf(
			"http://release-assets.githubusercontent.com/asset", "https://example.org/asset",
			"https://github.com/other-owner/other-repo/asset", "https://user@objects.githubusercontent.com/asset",
			"https://objects.githubusercontent.com:444/asset", "https://objects.githubusercontent.com.evil.example/asset",
		).forEach { isKrispyAssetRedirect(it).shouldBe(false) }
	}
})
