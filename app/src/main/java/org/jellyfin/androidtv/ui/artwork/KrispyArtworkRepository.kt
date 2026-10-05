package org.jellyfin.androidtv.ui.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.remoteImageApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageProviderInfo
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.RemoteImageInfo
import org.jellyfin.sdk.model.api.RemoteImageResult
import java.util.UUID
import java.security.MessageDigest

internal val krispyArtworkTypes = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.LOGO)

fun canEditKrispyArtwork(administrator: Boolean, type: BaseItemKind?) = administrator && when (type) {
	BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE -> true
	else -> false
}

data class ArtworkSlot(val type: ImageType, val image: ImageInfo?) {
	val index get() = image?.imageIndex ?: 0
	val key get() = "${type.serialName}:$index"
}

internal fun artworkSlots(images: List<ImageInfo>) = krispyArtworkTypes.flatMap { type ->
	images.filter { it.imageType == type }.sortedBy { it.imageIndex ?: 0 }
		.map { ArtworkSlot(type, it) }.ifEmpty { listOf(ArtworkSlot(type, null)) }
}

/** Narrow interface so replacement ordering and failure behavior can be tested without HTTP. */
internal interface ArtworkBackend {
	suspend fun images(item: UUID): List<ImageInfo>
	suspend fun backdropFingerprint(item: UUID, index: Int): String
	suspend fun download(item: UUID, type: ImageType, url: String)
	suspend fun swap(item: UUID, first: Int, second: Int)
	suspend fun delete(item: UUID, type: ImageType, index: Int)
}

internal fun checkArtworkSnapshot(slot: ArtworkSlot, images: List<ImageInfo>) {
	val current = images.firstOrNull { it.imageType == slot.type && (it.imageIndex ?: 0) == slot.index }
	check(current?.imageTag == slot.image?.imageTag && (current == null) == (slot.image == null)) {
		"Artwork changed on the server. Reload before editing it."
	}
}

/** Jellyfin appends backdrops and swaps indices; it does not move images between indices. */
internal suspend fun replaceArtwork(
	backend: ArtworkBackend, item: UUID, slot: ArtworkSlot, url: String, authorize: () -> Unit,
) {
	val before = backend.images(item)
	checkArtworkSnapshot(slot, before)
	val originalFingerprint = if (slot.type == ImageType.BACKDROP && slot.image != null) backend.backdropFingerprint(item, slot.index) else null
	authorize()
	backend.download(item, slot.type, url)
	if (slot.type != ImageType.BACKDROP || slot.image == null) return
	val after = backend.images(item).filter { it.imageType == ImageType.BACKDROP }
	val original = before.filter { it.imageType == ImageType.BACKDROP }
	check(after.size == original.size + 1) { "The server did not add a new backdrop. Existing artwork was kept." }
	val added = after.singleOrNull { candidate -> original.none { it.imageTag == candidate.imageTag } }
		?: error("The new backdrop could not be identified. Existing artwork was kept.")
	val newIndex = requireNotNull(added.imageIndex)
	// Verify the selected original is still there before the swap and deletion.
	checkArtworkSnapshot(slot, after)
	authorize()
	backend.swap(item, newIndex, slot.index)
	val swapped = backend.images(item).filter { it.imageType == ImageType.BACKDROP }
	// Jellyfin swaps file contents, so cache tags change with their paths. Verify the old image's
	// content at the appended index before deleting it; comparing pre-swap tags would be incorrect.
	check(swapped.any { it.imageIndex == slot.index } &&
		backend.backdropFingerprint(item, newIndex) == originalFingerprint) {
		"Artwork changed during replacement. No image was deleted."
	}
	authorize()
	// The old selected backdrop is now in the appended slot. Other backdrops stay in place.
	backend.delete(item, ImageType.BACKDROP, newIndex)
}

data class ArtworkChange(val item: UUID, val revision: Long)

internal suspend fun deleteArtwork(backend: ArtworkBackend, item: UUID, slot: ArtworkSlot, authorize: () -> Unit) {
	require(slot.image != null && slot.type in krispyArtworkTypes)
	checkArtworkSnapshot(slot, backend.images(item))
	authorize()
	backend.delete(item, slot.type, slot.index)
}

class KrispyArtworkRepository(private val api: ApiClient, private val users: UserRepository) {
	private val _changes = MutableStateFlow<ArtworkChange?>(null)
	val changes = _changes.asStateFlow()
	private val backend = object : ArtworkBackend {
		override suspend fun images(item: UUID) = api.imageApi.getItemImageInfos(item).content
		override suspend fun backdropFingerprint(item: UUID, index: Int): String {
			// A lossless format yields the same content after the server moves it to another path.
			val bytes = api.imageApi.getItemImage(itemId = item, imageType = ImageType.BACKDROP,
				imageIndex = index, format = ImageFormat.PNG, tag = UUID.randomUUID().toString()).content
			return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
		}
		override suspend fun download(item: UUID, type: ImageType, url: String) {
			api.remoteImageApi.downloadRemoteImage(item, type, url)
		}
		override suspend fun swap(item: UUID, first: Int, second: Int) {
			api.imageApi.updateItemImageIndex(item, ImageType.BACKDROP, first, second)
		}
		override suspend fun delete(item: UUID, type: ImageType, index: Int) {
			api.imageApi.deleteItemImage(item, type, index)
		}
	}

	private fun requireAdmin() {
		check(users.currentUser.value?.policy?.isAdministrator == true) { "Administrator access is required." }
	}

	suspend fun item(id: UUID): BaseItemDto = withContext(Dispatchers.IO) {
		requireAdmin()
		api.userLibraryApi.getItem(id).content.also {
			check(canEditKrispyArtwork(true, it.type)) { "This item does not support artwork editing." }
		}
	}

	suspend fun images(id: UUID): List<ImageInfo> = withContext(Dispatchers.IO) {
		requireAdmin()
		backend.images(id)
	}

	suspend fun providers(id: UUID): List<ImageProviderInfo> = withContext(Dispatchers.IO) {
		requireAdmin()
		api.remoteImageApi.getRemoteImageProviders(id).content
	}

	suspend fun search(id: UUID, type: ImageType, offset: Int, provider: String?, allLanguages: Boolean): RemoteImageResult = withContext(Dispatchers.IO) {
		requireAdmin()
		require(type in krispyArtworkTypes && offset >= 0)
		api.remoteImageApi.getRemoteImages(id, type, offset, 24, provider, allLanguages).content
	}

	fun imageUrl(id: UUID, image: ImageInfo) = api.imageApi.getItemImageUrl(
		itemId = id, imageType = image.imageType, imageIndex = image.imageIndex,
		tag = image.imageTag, maxWidth = 600, maxHeight = 400,
	)

	suspend fun replace(id: UUID, slot: ArtworkSlot, image: RemoteImageInfo): Unit = withContext(Dispatchers.IO) {
		val actor = users.currentUser.value?.id
		item(id)
		require(image.type == slot.type && slot.type in krispyArtworkTypes)
		val url = requireNotNull(image.url?.takeIf(String::isNotBlank))
		try {
			replaceArtwork(backend, id, slot, url) {
				requireAdmin()
				check(users.currentUser.value?.id == actor) { "The signed-in user changed." }
			}
		} finally { changed(id) }
	}

	suspend fun delete(id: UUID, slot: ArtworkSlot): Unit = withContext(Dispatchers.IO) {
		val actor = users.currentUser.value?.id
		item(id)
		try {
			deleteArtwork(backend, id, slot) {
				requireAdmin()
				check(users.currentUser.value?.id == actor) { "The signed-in user changed." }
			}
		}
		finally { changed(id) }
	}

	private fun changed(id: UUID) {
		_changes.value = ArtworkChange(id, (_changes.value?.revision ?: 0) + 1)
	}
}
