package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

/** Uses the existing Coil loader/cache, trying actual usable item artwork before parent artwork. */
class KrispyHomeBackdropLoader(
	context: Context,
	private val api: ApiClient,
	private val imageLoader: ImageLoader,
) {
	private val context = context.applicationContext

	suspend fun load(item: BaseItemDto, includeLibraryArtwork: Boolean = false): ImageBitmap? = withContext(Dispatchers.IO) {
		val candidates = krispyHeroBackdropCandidates(item, includeLibraryArtwork)
		if (candidates.isEmpty()) return@withContext null

		val display = context.resources.displayMetrics
		val width = display.widthPixels.coerceAtLeast(1)
		val height = display.heightPixels.coerceAtLeast(1)
		for (image in candidates) {
			ensureActive()
			val request = ImageRequest.Builder(context)
				.data(image.getUrl(api, maxWidth = width, maxHeight = height))
				.size(width, height)
				.build()
			val loaded = imageLoader.execute(request).image ?: continue
			ensureActive()
			return@withContext loaded.toBitmap().asImageBitmap()
		}
		null
	}
}

internal fun krispyHeroBackdropCandidates(item: BaseItemDto, includeLibraryArtwork: Boolean = false) = buildList {
	addAll(item.itemBackdropImages)
	addAll(item.parentBackdropImages)
	if (includeLibraryArtwork) {
		item.itemImages[ImageType.THUMB]?.let(::add)
		item.itemImages[ImageType.PRIMARY]?.let(::add)
	}
}.filter { it.tag.isNotBlank() }.distinct()
