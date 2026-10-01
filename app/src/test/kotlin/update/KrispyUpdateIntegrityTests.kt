package org.jellyfin.androidtv.update

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.EmptyCoroutineContext

class KrispyUpdateIntegrityTests : FunSpec({
	val bytes = "signed APK fixture content".toByteArray()
	val hash = MessageDigest.getInstance("SHA-256").digest(bytes).toUpdateHex()
	test("a complete exact-size APK with a matching SHA-256 can proceed") {
		val output = ByteArrayOutputStream()
		ByteArrayInputStream(bytes).copyVerifiedUpdate(output, bytes.size.toLong(), hash.uppercase(), EmptyCoroutineContext)
		output.toByteArray().toList().shouldBe(bytes.toList())
	}
	test("modified bytes with the expected length are rejected") {
		val changed = bytes.copyOf().apply { this[0] = (this[0] + 1).toByte() }
		shouldThrow<KrispyUpdateException> {
			ByteArrayInputStream(changed).copyVerifiedUpdate(ByteArrayOutputStream(), bytes.size.toLong(), hash, EmptyCoroutineContext)
		}.reason.shouldBe(KrispyUpdateMessage.INVALID_APK)
	}
	test("truncated and oversized downloads are rejected") {
		listOf(bytes.dropLast(1).toByteArray(), bytes + 0).forEach {
			shouldThrow<KrispyUpdateException> {
				ByteArrayInputStream(it).copyVerifiedUpdate(ByteArrayOutputStream(), bytes.size.toLong(), hash, EmptyCoroutineContext)
			}
		}
	}
	test("cancelled work cannot produce an installable APK") {
		val job = Job().apply { cancel() }
		shouldThrow<CancellationException> {
			ByteArrayInputStream(bytes).copyVerifiedUpdate(ByteArrayOutputStream(), bytes.size.toLong(), hash, job)
		}
	}
	test("metadata reads are bounded and cancellation is respected") {
		shouldThrow<IllegalArgumentException> { readUpdateBytes(ByteArrayInputStream(bytes), 1, EmptyCoroutineContext) }
		shouldThrow<CancellationException> { readUpdateBytes(ByteArrayInputStream(bytes), 100, Job().apply { cancel() }) }
	}
	val installed = KrispyApkIdentity("io.github.crunchyinmilk.krispy", 100, "older", setOf("release-certificate"))
	val archive = installed.copy(versionCode = updateTestMetadata.versionCode, versionName = updateTestMetadata.versionName, minimumSdk = 23)
	test("the same package and permanent signing certificate with a higher exact version are accepted") {
		validateKrispyApkIdentity(archive, installed, updateTestMetadata, 23)
	}
	listOf(
		"official Jellyfin package" to archive.copy(packageName = "org.jellyfin.androidtv"),
		"debug package" to archive.copy(packageName = "io.github.crunchyinmilk.krispy.debug"),
		"wrong signing certificate" to archive.copy(certificates = setOf("other-key")),
		"unsigned APK" to archive.copy(certificates = emptySet()),
		"incorrect version code" to archive.copy(versionCode = 102),
		"incorrect version name" to archive.copy(versionName = "other"),
		"unsupported Android version" to archive.copy(minimumSdk = 24),
	).forEach { (label, invalid) ->
		test("reject " + label + " before installer handoff") {
			shouldThrow<KrispyUpdateException> { validateKrispyApkIdentity(invalid, installed, updateTestMetadata, 23) }
		}
	}
	test("an equal or lower APK version cannot update the installed app") {
		shouldThrow<KrispyUpdateException> {
			validateKrispyApkIdentity(archive, installed.copy(versionCode = archive.versionCode), updateTestMetadata, 23)
		}
	}
})
