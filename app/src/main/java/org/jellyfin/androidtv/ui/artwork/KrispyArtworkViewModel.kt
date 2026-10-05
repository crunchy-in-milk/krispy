package org.jellyfin.androidtv.ui.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageProviderInfo
import org.jellyfin.sdk.model.api.RemoteImageInfo
import timber.log.Timber
import java.util.UUID

data class ArtworkState(
	val item: BaseItemDto? = null,
	val slots: List<ArtworkSlot> = emptyList(),
	val providers: List<ImageProviderInfo> = emptyList(),
	val target: ArtworkSlot? = null,
	val provider: String? = null,
	val allLanguages: Boolean = false,
	val results: List<RemoteImageInfo> = emptyList(),
	val offset: Int = 0,
	val total: Int = 0,
	val loading: Boolean = true,
	val busy: Boolean = false,
	val error: Int? = null,
	val notice: Int? = null,
)

class KrispyArtworkViewModel(private val repository: KrispyArtworkRepository) : ViewModel() {
	private val _state = MutableStateFlow(ArtworkState())
	val state = _state.asStateFlow()
	private var id: UUID? = null
	private var searchJob: Job? = null

	fun load(item: UUID) {
		if (id == item && _state.value.item != null) return
		id = item
		reload()
	}

	fun reload() = viewModelScope.launch {
		val itemId = id ?: return@launch
		_state.value = _state.value.copy(loading = true, error = null, notice = null)
		try {
			val item = repository.item(itemId)
			val slots = artworkSlots(repository.images(itemId))
			val providers = try { repository.providers(itemId) }
			catch (error: Exception) {
				if (error is CancellationException) throw error
				// The artwork screen remains usable if provider discovery fails; search can still try All.
				emptyList()
			}
			_state.value = _state.value.copy(item = item, slots = slots, providers = providers, loading = false)
		} catch (error: Exception) { failure(error, R.string.krispy_artwork_load_failed) }
	}

	fun search(slot: ArtworkSlot, provider: String? = null, allLanguages: Boolean = false, offset: Int = 0) {
		if (_state.value.busy) return
		val itemId = id ?: return
		searchJob?.cancel()
		_state.value = _state.value.copy(target = slot, provider = provider, allLanguages = allLanguages,
			results = emptyList(), offset = offset, total = 0, loading = true, error = null, notice = null)
		searchJob = viewModelScope.launch {
			try {
				val page = repository.search(itemId, slot.type, offset, provider, allLanguages)
				_state.value = _state.value.copy(results = page.images.orEmpty().filter { !it.url.isNullOrBlank() && it.type == slot.type },
					total = page.totalRecordCount, loading = false)
			} catch (error: Exception) { failure(error, R.string.krispy_artwork_search_failed) }
		}
	}

	fun backToImages() {
		if (_state.value.busy) return
		searchJob?.cancel()
		_state.value = _state.value.copy(target = null, loading = false, error = null, notice = null)
	}

	fun replace(image: RemoteImageInfo) {
		val slot = _state.value.target ?: return
		mutate { repository.replace(requireNotNull(id), slot, image) }
	}

	fun delete(slot: ArtworkSlot) = mutate { repository.delete(requireNotNull(id), slot) }

	private fun mutate(action: suspend () -> Unit) {
		if (_state.value.busy) return
		_state.value = _state.value.copy(busy = true, error = null, notice = null)
		viewModelScope.launch {
			try {
				action()
				_state.value = _state.value.copy(slots = artworkSlots(repository.images(requireNotNull(id))),
					target = null, busy = false, loading = false, notice = R.string.krispy_artwork_saved)
			} catch (error: Exception) {
				failure(error, R.string.krispy_artwork_save_failed)
				// A backdrop operation can fail after adding/swapping. Show the actual server state.
				try { _state.value = _state.value.copy(slots = artworkSlots(repository.images(requireNotNull(id))), target = null) }
				catch (refreshError: Exception) { if (refreshError is CancellationException) throw refreshError }
			}
		}
	}

	private fun failure(error: Exception, message: Int) {
		if (error is CancellationException) throw error
		Timber.w(error, "Artwork operation failed")
		_state.value = _state.value.copy(loading = false, busy = false, error = message)
	}

	fun imageUrl(slot: ArtworkSlot) = slot.image?.let { repository.imageUrl(requireNotNull(id), it) }
}
