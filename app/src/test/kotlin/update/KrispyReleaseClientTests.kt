package org.jellyfin.androidtv.update

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

class KrispyReleaseClientTests : FunSpec({
	test("no published release returns no update and performs no asset download") {
		val http = mockk<KrispyUpdateHttp>()
		coEvery { http.read(updateTestPolicy.latestReleaseUrl, any(), true) } returns null
		KrispyReleaseClient(http, updateTestPolicy).latest().shouldBe(null)
		coVerify(exactly = 1) { http.read(any(), any(), any()) }
	}
	listOf(true to false, false to true).forEach { (draft, prerelease) ->
		test("ignore draft=" + draft + " prerelease=" + prerelease) {
			val http = mockk<KrispyUpdateHttp>()
			val release = updateTestRelease().copy(draft = draft, prerelease = prerelease)
			coEvery { http.read(updateTestPolicy.latestReleaseUrl, any(), true) } returns krispyUpdateJson.encodeToString(release).toByteArray()
			KrispyReleaseClient(http, updateTestPolicy).latest().shouldBe(null)
			coVerify(exactly = 1) { http.read(any(), any(), any()) }
		}
	}
	test("read numeric APK version from verified metadata, independent of its human-readable tag") {
		val http = mockk<KrispyUpdateHttp>()
		val bytes = krispyUpdateJson.encodeToString(updateTestMetadata.copy(versionCode = 123456)).toByteArray()
		val asset = updateTestAsset(KRISPY_UPDATE_METADATA).copy(
			size = bytes.size.toLong(),
			digest = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).toUpdateHex(),
		)
		val release = updateTestRelease().copy(assets = listOf(updateTestAsset(), asset))
		coEvery { http.read(updateTestPolicy.latestReleaseUrl, any(), true) } returns krispyUpdateJson.encodeToString(release).toByteArray()
		coEvery { http.read(asset.downloadUrl, any(), false) } returns bytes
		KrispyReleaseClient(http, updateTestPolicy).latest()?.metadata?.versionCode.shouldBe(123456L)
	}
	test("metadata tampering is rejected before an APK can be selected") {
		val http = mockk<KrispyUpdateHttp>()
		val bytes = krispyUpdateJson.encodeToString(updateTestMetadata).toByteArray()
		val asset = updateTestAsset(KRISPY_UPDATE_METADATA).copy(size = bytes.size.toLong(), digest = "sha256:" + "b".repeat(64))
		coEvery { http.read(updateTestPolicy.latestReleaseUrl, any(), true) } returns
			krispyUpdateJson.encodeToString(updateTestRelease().copy(assets = listOf(updateTestAsset(), asset))).toByteArray()
		coEvery { http.read(asset.downloadUrl, any(), false) } returns bytes
		shouldThrow<IllegalArgumentException> { KrispyReleaseClient(http, updateTestPolicy).latest() }
	}
})
