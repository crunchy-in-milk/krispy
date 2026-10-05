package org.jellyfin.androidtv.ui.itemdetail

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class KrispyDetailsMetadataTests : FunSpec({
	test("opting out keeps all detail types on the classic layout") {
		BaseItemKind.entries.forEach { useKrispyEnhancedDetails(false, it) shouldBe false }
	}

	test("enhanced details apply to movie and TV items while other detail types stay classic") {
		listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
			.forEach { useKrispyEnhancedDetails(true, it) shouldBe true }
		listOf(BaseItemKind.PERSON, BaseItemKind.MUSIC_ARTIST, BaseItemKind.MUSIC_ALBUM, BaseItemKind.PROGRAM, BaseItemKind.TV_CHANNEL, null)
			.forEach { useKrispyEnhancedDetails(true, it) shouldBe false }
	}

	test("missing genres or seasons do not leave empty separators") {
		val item = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, genres = listOf("Animation", " ", " Comedy "))
		item.krispyDetailsGenres("8 Seasons") shouldBe "Animation / Comedy • 8 Seasons"
		item.krispyDetailsGenres() shouldBe "Animation / Comedy"
		item.copy(genres = null).krispyDetailsGenres("1 Season") shouldBe "1 Season"
		item.copy(genres = null).krispyDetailsGenres() shouldBe ""
	}
})
