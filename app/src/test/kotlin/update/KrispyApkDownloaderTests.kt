package org.jellyfin.androidtv.update

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class KrispyApkDownloaderTests : FunSpec({
	test("verified content remains in private storage for the installer and is checked again before handoff") {
		runTest {
			val directory = Files.createTempDirectory("krispy-update-test").toFile()
			try {
				val bytes = "valid signed package fixture".toByteArray()
				val update = updateTestApproved().copy(
					size = bytes.size.toLong(),
					metadata = updateTestMetadata.copy(sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toUpdateHex()),
				)
				val http = mockk<KrispyUpdateHttp>()
				val verifier = mockk<KrispyApkVerifier>()
				coEvery { http.download(update, any(), any()) } coAnswers { secondArg<File>().writeBytes(bytes) }
				every { verifier.verify(any(), update.metadata) } returns Unit
				val downloader = KrispyApkDownloader(http, verifier, directory)
				val apk = downloader.prepare(update) {}
				apk.parentFile.shouldBe(directory)
				apk.extension.shouldBe("apk")
				directory.listFiles()?.count { it.extension == "part" }.shouldBe(0)
				downloader.verify(apk, update)
				verify(exactly = 2) { verifier.verify(any(), update.metadata) }
				apk.writeBytes(bytes.copyOf().apply { this[0] = 0 })
				shouldThrow<KrispyUpdateException> { downloader.verify(apk, update) }
				verify(exactly = 2) { verifier.verify(any(), update.metadata) }
			} finally {
				directory.deleteRecursively()
			}
		}
	}
	test("a signature rejection deletes the partial file and never leaves an installable APK") {
		runTest {
			val directory = Files.createTempDirectory("krispy-update-test").toFile()
			try {
				val http = mockk<KrispyUpdateHttp>()
				val verifier = mockk<KrispyApkVerifier>()
				coEvery { http.download(any(), any(), any()) } coAnswers { secondArg<File>().writeText("wrong signing key") }
				every { verifier.verify(any(), any()) } throws KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
				shouldThrow<KrispyUpdateException> { KrispyApkDownloader(http, verifier, directory).prepare(updateTestApproved()) {} }
				directory.listFiles()?.size.shouldBe(0)
			} finally {
				directory.deleteRecursively()
			}
		}
	}
	test("cancelled transfer deletes its partial file and cannot reach signature verification") {
		runTest {
			val directory = Files.createTempDirectory("krispy-update-test").toFile()
			try {
				val http = mockk<KrispyUpdateHttp>()
				val verifier = mockk<KrispyApkVerifier>()
				val started = CompletableDeferred<Unit>()
				coEvery { http.download(any(), any(), any()) } coAnswers {
					secondArg<File>().writeText("partial")
					started.complete(Unit)
					awaitCancellation()
				}
				val download = launch { KrispyApkDownloader(http, verifier, directory).prepare(updateTestApproved()) {} }
				started.await()
				download.cancel()
				download.join()
				directory.listFiles()?.size.shouldBe(0)
				verify(exactly = 0) { verifier.verify(any(), any()) }
				coVerify(exactly = 1) { http.download(any(), any(), any()) }
			} finally {
				directory.deleteRecursively()
			}
		}
	}
})
