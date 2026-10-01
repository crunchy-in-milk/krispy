package org.jellyfin.androidtv.ui.home

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class KrispyHomeViewModelTests : FunSpec({
	test("classic Home leaves background handling to the rows and never loads a hero") {
		runTest {
			withHome {
				viewModel.startHome()
				viewModel.onItemFocused(row(item("Classic")))
				runCurrent()
				advanceTimeBy(500)
				runCurrent()
				viewModel.stopHome()

				viewModel.enabled.value shouldBe false
				viewModel.hero.value shouldBe KrispyHomeHeroState()
				verify(exactly = 0) { backgrounds.disable() }
				verify(exactly = 0) { backgrounds.clearBackgrounds() }
				verify(exactly = 0) { backgrounds.setBackground(any<BaseItemDto>()) }
				coVerify(exactly = 0) { loader.load(any()) }
			}
		}
	}

	test("an already enabled preference disables the background synchronously on entry") {
		runTest {
			withHome(initialEnabled = true) {
				viewModel.startHome()
				verify(exactly = 1) { backgrounds.disable() }
				viewModel.enabled.value shouldBe true
			}
		}
	}

	test("live ON loads current focus and live OFF immediately restores its stock backdrop") {
		runTest {
			withHome {
				val selected = item("Selected")
				viewModel.onItemFocused(row(selected))
				viewModel.startHome()
				runCurrent()
				preference.value = true
				runCurrent()
				verify(exactly = 1) { backgrounds.disable() }
				advanceTimeBy(199)
				runCurrent()
				coVerify(exactly = 0) { loader.load(any()) }
				advanceTimeBy(1)
				runCurrent()
				viewModel.hero.value.item shouldBe selected

				preference.value = false
				runCurrent()
				verify(exactly = 1) { backgrounds.setBackground(selected) }
				viewModel.hero.value shouldBe KrispyHomeHeroState()
			}
		}
	}

	test("live OFF without media focus restores a cleared stock background") {
		runTest {
			withHome(initialEnabled = true) {
				viewModel.startHome()
				runCurrent()
				preference.value = false
				runCurrent()
				verify(exactly = 1) { backgrounds.clearBackgrounds() }
				verify(exactly = 0) { backgrounds.setBackground(any<BaseItemDto>()) }
			}
		}
	}

	test("rapid D-pad changes load only the final item after 200 ms of stable focus") {
		runTest {
			withHome(initialEnabled = true) {
				val first = item("First")
				val second = item("Second")
				val last = item("Last")
				viewModel.startHome()
				viewModel.onItemFocused(row(first))
				runCurrent()
				advanceTimeBy(100)
				viewModel.onItemFocused(row(second))
				runCurrent()
				advanceTimeBy(100)
				viewModel.onItemFocused(row(last))
				runCurrent()
				advanceTimeBy(199)
				runCurrent()
				coVerify(exactly = 0) { loader.load(any()) }
				advanceTimeBy(1)
				runCurrent()
				coVerify(exactly = 1) { loader.load(last) }
				coVerify(exactly = 0) { loader.load(first) }
				coVerify(exactly = 0) { loader.load(second) }
				viewModel.hero.value.title shouldBe "Last"
			}
		}
	}

	test("artwork and metadata stay together until their replacement is loaded") {
		runTest {
			withHome(initialEnabled = true) {
				val first = item("First")
				val next = item("Next")
				val firstImage = mockk<ImageBitmap>()
				val nextImage = mockk<ImageBitmap>()
				val pending = CompletableDeferred<ImageBitmap?>()
				coEvery { loader.load(first) } returns firstImage
				coEvery { loader.load(next) } coAnswers { pending.await() }
				viewModel.startHome()
				viewModel.onItemFocused(row(first))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				viewModel.onItemFocused(row(next))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				viewModel.hero.value shouldBe KrispyHomeHeroState(first, "First", firstImage)

				pending.complete(nextImage)
				runCurrent()
				viewModel.hero.value shouldBe KrispyHomeHeroState(next, "Next", nextImage)
			}
		}
	}

	test("a cancelled in-flight request cannot replace a later selected hero") {
		runTest {
			withHome(initialEnabled = true) {
				val old = item("Old")
				val latest = item("Latest")
				val pending = CompletableDeferred<ImageBitmap?>()
				coEvery { loader.load(old) } coAnswers { pending.await() }
				viewModel.startHome()
				viewModel.onItemFocused(row(old))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				viewModel.onItemFocused(row(latest))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				pending.complete(mockk<ImageBitmap>())
				runCurrent()
				viewModel.hero.value.item shouldBe latest
				viewModel.hero.value.title shouldBe "Latest"
			}
		}
	}

	test("live OFF cancels an in-flight load before it can repopulate the hero") {
		runTest {
			withHome(initialEnabled = true) {
				val selected = item("Selected")
				val pending = CompletableDeferred<ImageBitmap?>()
				coEvery { loader.load(selected) } coAnswers { pending.await() }
				viewModel.startHome()
				viewModel.onItemFocused(row(selected))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				preference.value = false
				runCurrent()
				pending.complete(mockk<ImageBitmap>())
				runCurrent()
				viewModel.hero.value shouldBe KrispyHomeHeroState()
				verify(exactly = 1) { backgrounds.setBackground(selected) }
			}
		}
	}

	test("leaving Home clears suppression and cancels a pending hero") {
		runTest {
			withHome(initialEnabled = true) {
				viewModel.startHome()
				viewModel.onItemFocused(row(item("Leaving")))
				runCurrent()
				advanceTimeBy(100)
				viewModel.stopHome()
				advanceTimeBy(200)
				runCurrent()
				verify(exactly = 1) { backgrounds.clearBackgrounds() }
				coVerify(exactly = 0) { loader.load(any()) }
				viewModel.hero.value shouldBe KrispyHomeHeroState()

				viewModel.startHome()
				advanceTimeBy(200)
				runCurrent()
				coVerify(exactly = 0) { loader.load(any()) }
			}
		}
	}

	test("leaving Home during a load prevents publication after navigation") {
		runTest {
			withHome(initialEnabled = true) {
				val pending = CompletableDeferred<ImageBitmap?>()
				coEvery { loader.load(any()) } coAnswers { pending.await() }
				viewModel.startHome()
				viewModel.onItemFocused(row(item("Leaving")))
				runCurrent()
				advanceTimeBy(200)
				runCurrent()
				viewModel.stopHome()
				pending.complete(mockk<ImageBitmap>())
				runCurrent()
				viewModel.hero.value shouldBe KrispyHomeHeroState()
				verify(exactly = 1) { backgrounds.clearBackgrounds() }
			}
		}
	}

	test("preference changes away from Home never touch another screen's background") {
		runTest {
			withHome {
				runCurrent()
				preference.value = true
				runCurrent()
				viewModel.enabled.value shouldBe true
				verify(exactly = 0) { backgrounds.disable() }
				verify(exactly = 0) { backgrounds.clearBackgrounds() }
				verify(exactly = 0) { backgrounds.setBackground(any<BaseItemDto>()) }
			}
		}
	}

	test("non-media focus cancels pending hero work without re-enabling the global backdrop") {
		runTest {
			withHome(initialEnabled = true) {
				viewModel.startHome()
				viewModel.onItemFocused(row(item("Media")))
				runCurrent()
				advanceTimeBy(100)
				viewModel.onItemFocused(null)
				advanceTimeBy(200)
				runCurrent()
				coVerify(exactly = 0) { loader.load(any()) }
				viewModel.hero.value shouldBe KrispyHomeHeroState()
				verify(exactly = 0) { backgrounds.clearBackgrounds() }
			}
		}
	}
})

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.withHome(
	initialEnabled: Boolean = false,
	block: suspend KrispyHomeTestFixture.() -> Unit,
) {
	Dispatchers.setMain(StandardTestDispatcher(testScheduler))
	var fixture: KrispyHomeTestFixture? = null
	try {
		fixture = KrispyHomeTestFixture(initialEnabled)
		fixture.block()
	} finally {
		try {
			fixture?.close()
			runCurrent()
		} finally {
			Dispatchers.resetMain()
		}
	}
}

private class KrispyHomeTestFixture(initialEnabled: Boolean) {
	val preference = MutableStateFlow(initialEnabled)
	val application = mockk<Application>()
	val backgrounds = mockk<BackgroundService>(relaxed = true)
	val loader = mockk<KrispyHomeBackdropLoader> {
		coEvery { load(any()) } returns null
	}
	private val userPreferences = mockk<UserPreferences> {
		every { this@mockk[UserPreferences.krispyEnhancedHomeEnabled] } answers { preference.value }
		every { observeKrispyEnhancedHomeEnabled() } returns preference
	}
	val viewModel = KrispyHomeViewModel(application, backgrounds, userPreferences, loader)
	private val store = ViewModelStore().apply { put("home", viewModel) }

	fun row(item: BaseItemDto): BaseRowItem = mockk {
		every { baseItem } returns item
		every { getFullName(application) } returns item.name
	}

	fun close() {
		viewModel.stopHome()
		store.clear()
	}
}

private fun item(name: String) = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, name = name)
