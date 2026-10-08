package org.jellyfin.androidtv.ui.home

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest

internal enum class KrispyHomeMediaCountKind {
	SERIES,
	MOVIES,
	CHANNELS,
	ALBUMS,
	PLAYLISTS,
	ITEMS,
}

internal data class KrispyHomeMediaCount(
	val value: Int,
	val kind: KrispyHomeMediaCountKind,
)

internal class KrispyHomeMediaRowItem(
	item: BaseItemDto,
	internal val count: KrispyHomeMediaCount?,
) : BaseItemDtoBaseRowItem(item, staticHeight = true) {
	internal fun withRefreshedItem(item: BaseItemDto) = KrispyHomeMediaRowItem(item, count)

	override fun getSubText(context: Context): String? = count?.let { (value, kind) ->
		val resource = when (kind) {
			KrispyHomeMediaCountKind.SERIES -> R.plurals.krispy_series_count
			KrispyHomeMediaCountKind.MOVIES -> R.plurals.krispy_movie_count
			KrispyHomeMediaCountKind.CHANNELS -> R.plurals.krispy_channel_count
			KrispyHomeMediaCountKind.ALBUMS -> R.plurals.krispy_album_count
			KrispyHomeMediaCountKind.PLAYLISTS -> R.plurals.krispy_playlist_count
			KrispyHomeMediaCountKind.ITEMS -> R.plurals.krispy_item_count
		}
		context.resources.getQuantityString(resource, value, value)
	}
}

internal fun BaseItemDto.krispyHomeMediaCount(): KrispyHomeMediaCount? {
	val (value, kind) = when (collectionType) {
		CollectionType.TVSHOWS -> (seriesCount ?: childCount) to KrispyHomeMediaCountKind.SERIES
		CollectionType.MOVIES -> (movieCount ?: childCount) to KrispyHomeMediaCountKind.MOVIES
		CollectionType.LIVETV -> childCount to KrispyHomeMediaCountKind.CHANNELS
		CollectionType.MUSIC -> (albumCount ?: childCount) to KrispyHomeMediaCountKind.ALBUMS
		CollectionType.PLAYLISTS -> childCount to KrispyHomeMediaCountKind.PLAYLISTS
		else -> childCount to KrispyHomeMediaCountKind.ITEMS
	}
	return value?.let { KrispyHomeMediaCount(it, kind) }
}

internal fun BaseItemDto.requiresKrispyHomeMediaContentsCount() = collectionType in setOf(
	CollectionType.TVSHOWS,
	CollectionType.MOVIES,
	CollectionType.MUSIC,
	CollectionType.PLAYLISTS,
	CollectionType.LIVETV,
)

internal suspend fun ApiClient.resolveKrispyHomeMediaCount(item: BaseItemDto): KrispyHomeMediaCount? {
	if (!item.requiresKrispyHomeMediaContentsCount()) {
		item.krispyHomeMediaCount()?.let { return it }
	}

	val (count, kind) = when (item.collectionType) {
		CollectionType.LIVETV -> {
			liveTvApi.getLiveTvChannels(GetLiveTvChannelsRequest(limit = 1)).content.totalRecordCount to
				KrispyHomeMediaCountKind.CHANNELS
		}

		else -> {
			val itemType = when (item.collectionType) {
				CollectionType.TVSHOWS -> BaseItemKind.SERIES
				CollectionType.MOVIES -> BaseItemKind.MOVIE
				CollectionType.MUSIC -> BaseItemKind.MUSIC_ALBUM
				CollectionType.PLAYLISTS -> BaseItemKind.PLAYLIST
				else -> null
			}
			val response = itemsApi.getItems(
				GetItemsRequest(
					parentId = item.id,
					includeItemTypes = itemType?.let(::setOf),
					recursive = itemType != null,
					enableImages = false,
					limit = 1,
				)
			).content
			val countKind = when (item.collectionType) {
				CollectionType.TVSHOWS -> KrispyHomeMediaCountKind.SERIES
				CollectionType.MOVIES -> KrispyHomeMediaCountKind.MOVIES
				CollectionType.MUSIC -> KrispyHomeMediaCountKind.ALBUMS
				CollectionType.PLAYLISTS -> KrispyHomeMediaCountKind.PLAYLISTS
				else -> KrispyHomeMediaCountKind.ITEMS
			}
			response.totalRecordCount to countKind
		}
	}
	return KrispyHomeMediaCount(count, kind)
}
