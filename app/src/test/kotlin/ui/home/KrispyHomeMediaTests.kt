package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID

private fun mediaLibrary(
	collectionType: CollectionType,
	childCount: Int? = null,
	movieCount: Int? = null,
	seriesCount: Int? = null,
	albumCount: Int? = null,
) = BaseItemDto(
	id = UUID.randomUUID(),
	type = BaseItemKind.USER_VIEW,
	collectionType = collectionType,
	childCount = childCount,
	movieCount = movieCount,
	seriesCount = seriesCount,
	albumCount = albumCount,
)

class KrispyHomeMediaTests : FunSpec({
	test("library view metadata is recognized but requires an authoritative contents query") {
		mediaLibrary(CollectionType.TVSHOWS, childCount = 900, seriesCount = 142).krispyHomeMediaCount()
			.shouldBe(KrispyHomeMediaCount(142, KrispyHomeMediaCountKind.SERIES))
		mediaLibrary(CollectionType.MOVIES, childCount = 900, movieCount = 684).krispyHomeMediaCount()
			.shouldBe(KrispyHomeMediaCount(684, KrispyHomeMediaCountKind.MOVIES))
		mediaLibrary(CollectionType.MUSIC, childCount = 900, albumCount = 73).krispyHomeMediaCount()
			.shouldBe(KrispyHomeMediaCount(73, KrispyHomeMediaCountKind.ALBUMS))

		mediaLibrary(CollectionType.TVSHOWS).requiresKrispyHomeMediaContentsCount().shouldBe(true)
		mediaLibrary(CollectionType.MOVIES).requiresKrispyHomeMediaContentsCount().shouldBe(true)
		mediaLibrary(CollectionType.MUSIC).requiresKrispyHomeMediaContentsCount().shouldBe(true)
	}

	test("generic child counts use collection-aware labels") {
		mediaLibrary(CollectionType.LIVETV, childCount = 38).krispyHomeMediaCount()
			.shouldBe(KrispyHomeMediaCount(38, KrispyHomeMediaCountKind.CHANNELS))
		mediaLibrary(CollectionType.PLAYLISTS, childCount = 12).krispyHomeMediaCount()
			.shouldBe(KrispyHomeMediaCount(12, KrispyHomeMediaCountKind.PLAYLISTS))
	}

	test("missing counts request server enrichment") {
		mediaLibrary(CollectionType.MOVIES).krispyHomeMediaCount().shouldBe(null)
	}

	test("refreshing a media card preserves its resolved count") {
		val count = KrispyHomeMediaCount(684, KrispyHomeMediaCountKind.MOVIES)
		val original = KrispyHomeMediaRowItem(mediaLibrary(CollectionType.MOVIES), count)
		val refreshedItem = mediaLibrary(CollectionType.MOVIES)

		original.withRefreshedItem(refreshedItem).apply {
			baseItem.shouldBe(refreshedItem)
			this.count.shouldBe(count)
		}
	}
})
