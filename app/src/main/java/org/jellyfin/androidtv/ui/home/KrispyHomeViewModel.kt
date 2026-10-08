package org.jellyfin.androidtv.ui.home

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration.Companion.milliseconds

data class KrispyHomeHeroState(
	val item: BaseItemDto? = null,
	val title: String? = null,
	val backdrop: ImageBitmap? = null,
	val libraryAmbient: Boolean = false,
)

/** HomeFragment owns this state; row fragments report focus without owning background suppression. */
class KrispyHomeViewModel(
	application: Application,
	private val backgroundService: BackgroundService,
	private val userPreferences: UserPreferences,
	private val backdropLoader: KrispyHomeBackdropLoader,
) : AndroidViewModel(application) {
	private val _enabled = MutableStateFlow(userPreferences[UserPreferences.krispyEnhancedHomeEnabled])
	val enabled = _enabled.asStateFlow()

	private val _hero = MutableStateFlow(KrispyHomeHeroState())
	val hero = _hero.asStateFlow()

	private var homeActive = false
	private var focusedItem: BaseRowItem? = null
	private var heroJob: Job? = null

	init {
		viewModelScope.launch {
			userPreferences.observeKrispyEnhancedHomeEnabled().collect { applyPreference(it) }
		}
	}

	fun startHome() {
		if (homeActive) return
		// Read synchronously as well: a restored ON preference must apply on the first entry.
		_enabled.value = userPreferences[UserPreferences.krispyEnhancedHomeEnabled]
		homeActive = true
		if (enabled.value) {
			backgroundService.disable()
			loadFocusedHero()
		}
	}

	fun stopHome() {
		if (homeActive && enabled.value) backgroundService.clearBackgrounds()
		homeActive = false
		focusedItem = null
		clearHero()
	}

	fun onItemFocused(item: BaseRowItem?) {
		if (focusedItem === item) return
		focusedItem = item
		if (homeActive && enabled.value) loadFocusedHero()
	}

	private fun applyPreference(enabled: Boolean) {
		if (_enabled.value == enabled) return
		_enabled.value = enabled
		if (!homeActive) return

		if (enabled) {
			backgroundService.disable()
			loadFocusedHero()
		} else {
			clearHero()
			// Restore the same selected item/empty-selection behavior used by stock Home.
			val item = focusedItem
			if (item == null) backgroundService.clearBackgrounds()
			else backgroundService.setBackground(item.baseItem)
		}
	}

	private fun clearHero() {
		heroJob?.cancel()
		heroJob = null
		_hero.value = KrispyHomeHeroState()
	}

	private fun loadFocusedHero() {
		heroJob?.cancel()
		val rowItem = focusedItem
		val item = rowItem?.baseItem
		if (rowItem == null || item == null) {
			clearHero()
			return
		}

		heroJob = viewModelScope.launch {
			// Row focus is immediate; only hero work waits for a stable selection.
			delay(HERO_DEBOUNCE)
			val libraryAmbient = item.isKrispyHomeLibraryView()
			val title = if (libraryAmbient) null else rowItem.getFullName(getApplication<Application>())
			val backdrop = backdropLoader.load(item, includeLibraryArtwork = libraryAmbient)
			// Publish artwork and metadata together, retaining the old hero throughout loading.
			_hero.value = KrispyHomeHeroState(item, title, backdrop, libraryAmbient)
		}
	}

	private companion object {
		val HERO_DEBOUNCE = 200.milliseconds
	}
}

internal fun BaseItemDto.isKrispyHomeLibraryView() =
	type == BaseItemKind.USER_VIEW || type == BaseItemKind.COLLECTION_FOLDER
