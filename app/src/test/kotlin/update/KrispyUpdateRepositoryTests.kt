package org.jellyfin.androidtv.update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.hours

private class UpdateTestStore : KrispyUpdateStore {
	override var lastAttempt = 0L
	override var lastSuccessfulCheck = 0L
	override var cachedUpdate: KrispyApprovedUpdate? = null
}

private class UpdateRepositoryFixture(cached: KrispyApprovedUpdate? = null) {
	val source = mockk<KrispyReleaseClient>()
	val downloader = mockk<KrispyApkDownloader>()
	val store = UpdateTestStore().apply { cachedUpdate = cached }
	var now = 1_000_000L
	val repository = KrispyUpdateRepository(source, updateTestPolicy, store, downloader, 100) { now }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KrispyUpdateRepositoryTests : FunSpec({
	test("only a numerically higher version code produces an update prompt") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			for (code in listOf(99L, 100L, 101L)) {
				val update = updateTestApproved().copy(metadata = updateTestMetadata.copy(versionCode = code))
				coEvery { fixture.source.latest() } returns update
				fixture.repository.check(force = true)
				fixture.repository.state.value.available?.metadata?.versionCode.shouldBe(code.takeIf { it > 100 })
			}
		}
	}
	test("successful checks persist metadata and are throttled until twelve hours have passed") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			coEvery { fixture.source.latest() } returns updateTestApproved()
			fixture.repository.check()
			fixture.repository.check()
			fixture.store.cachedUpdate.shouldBe(updateTestApproved())
			fixture.store.lastSuccessfulCheck.shouldBe(fixture.now)
			coVerify(exactly = 1) { fixture.source.latest() }
			fixture.now += 12.hours.inWholeMilliseconds
			fixture.repository.check()
			coVerify(exactly = 2) { fixture.source.latest() }
		}
	}
	test("manual checks bypass the automatic interval") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			coEvery { fixture.source.latest() } returns null
			fixture.repository.check()
			fixture.repository.check(force = true)
			coVerify(exactly = 2) { fixture.source.latest() }
			fixture.repository.state.value.message.shouldBe(KrispyUpdateMessage.NO_RELEASE)
		}
	}
	test("a clock rollback does not suppress updates indefinitely") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			coEvery { fixture.source.latest() } returns null
			fixture.repository.check()
			fixture.now -= 1
			fixture.repository.check()
			coVerify(exactly = 2) { fixture.source.latest() }
		}
	}
	test("offline checks preserve a validated cached update and cannot flood requests on every Home resume") {
		runTest {
			val fixture = UpdateRepositoryFixture(updateTestApproved())
			coEvery { fixture.source.latest() } throws IOException("offline")
			fixture.repository.check()
			fixture.repository.check()
			fixture.repository.state.value.available.shouldBe(updateTestApproved())
			fixture.repository.state.value.message.shouldBe(KrispyUpdateMessage.CHECK_FAILED)
			fixture.repository.state.value.busy.shouldBe(false)
			fixture.store.lastSuccessfulCheck.shouldBe(0L)
			coVerify(exactly = 1) { fixture.source.latest() }
		}
	}
	test("cached arbitrary URLs cannot become available updates") {
		val fixture = UpdateRepositoryFixture(updateTestApproved().copy(downloadUrl = "https://example.org/evil.apk"))
		fixture.repository.state.value.available.shouldBe(null)
	}
	test("malformed release metadata is reported without throwing into Home") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			coEvery { fixture.source.latest() } throws IllegalArgumentException("invalid metadata")
			fixture.repository.check()
			fixture.repository.state.value.message.shouldBe(KrispyUpdateMessage.INVALID_RELEASE)
			fixture.repository.state.value.busy.shouldBe(false)
		}
	}
	test("an unpublished or removed release clears a previously available update") {
		runTest {
			val fixture = UpdateRepositoryFixture(updateTestApproved())
			coEvery { fixture.source.latest() } returns null
			fixture.repository.check()
			fixture.repository.state.value.available.shouldBe(null)
			fixture.store.cachedUpdate.shouldBe(null)
		}
	}
	test("concurrent checks share the in-flight operation") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			val result = CompletableDeferred<KrispyApprovedUpdate?>()
			coEvery { fixture.source.latest() } coAnswers { result.await() }
			val checking = launch { fixture.repository.check() }
			runCurrent()
			fixture.repository.check(force = true)
			coVerify(exactly = 1) { fixture.source.latest() }
			result.complete(updateTestApproved())
			checking.join()
			fixture.repository.state.value.available.shouldBe(updateTestApproved())
		}
	}
	test("cancelled checking releases its lock and does not publish a result") {
		runTest {
			val fixture = UpdateRepositoryFixture()
			coEvery { fixture.source.latest() } coAnswers { awaitCancellation() }
			val checking = launch { fixture.repository.check() }
			runCurrent()
			checking.cancel()
			checking.join()
			fixture.repository.state.value.busy.shouldBe(false)
			coEvery { fixture.source.latest() } returns updateTestApproved()
			fixture.repository.check(force = true)
			fixture.repository.state.value.available.shouldBe(updateTestApproved())
		}
	}
	test("a rejected APK never becomes ready for installation") {
		runTest {
			val fixture = UpdateRepositoryFixture(updateTestApproved())
			coEvery { fixture.downloader.prepare(any(), any()) } throws KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
			fixture.repository.download()
			fixture.repository.state.value.readyApk.shouldBe(null)
			fixture.repository.state.value.message.shouldBe(KrispyUpdateMessage.INVALID_APK)
			fixture.repository.state.value.busy.shouldBe(false)
		}
	}
	test("download and installer revalidation must both succeed before handoff") {
		runTest {
			val fixture = UpdateRepositoryFixture(updateTestApproved())
			val file = File("verified.apk")
			coEvery { fixture.downloader.prepare(any(), any()) } returns file
			coEvery { fixture.downloader.verify(file, any()) } throws KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
			fixture.repository.download()
			fixture.repository.state.value.readyApk.shouldBe(file)
			fixture.repository.verifiedApk().shouldBe(null)
			fixture.repository.state.value.readyApk.shouldBe(null)
		}
	}
	test("cancelling a download preserves the update but never enables installation") {
		runTest {
			val fixture = UpdateRepositoryFixture(updateTestApproved())
			coEvery { fixture.downloader.prepare(any(), any()) } coAnswers { awaitCancellation() }
			val download = launch { fixture.repository.download() }
			runCurrent()
			download.cancel()
			download.join()
			fixture.repository.state.value.available.shouldBe(updateTestApproved())
			fixture.repository.state.value.readyApk.shouldBe(null)
			fixture.repository.state.value.busy.shouldBe(false)
		}
	}
})
