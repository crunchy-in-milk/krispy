package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

class KrispyHomeBackdropTests : FunSpec({
	test("item candidates precede parent candidates and blank tags preserve original indices") {
		val itemId = UUID.randomUUID()
		val parentId = UUID.randomUUID()
		val item = BaseItemDto(
			id = itemId,
			type = BaseItemKind.MOVIE,
			backdropImageTags = listOf("", "item-art"),
			parentBackdropItemId = parentId,
			parentBackdropImageTags = listOf("parent-art"),
		)
		val candidates = krispyHeroBackdropCandidates(item)
		candidates.map { it.item }.shouldBe(listOf(itemId, parentId))
		candidates.map { it.tag }.shouldBe(listOf("item-art", "parent-art"))
		candidates.map { it.index }.shouldBe(listOf(1, 0))
	}

	test("an episode without item artwork still has its parent's backdrop candidate") {
		val parentId = UUID.randomUUID()
		val item = BaseItemDto(
			id = UUID.randomUUID(),
			type = BaseItemKind.EPISODE,
			parentBackdropItemId = parentId,
			parentBackdropImageTags = listOf("series-art"),
		)
		val candidates = krispyHeroBackdropCandidates(item)
		candidates.single().item.shouldBe(parentId)
		candidates.single().tag.shouldBe("series-art")
	}

	test("missing parent identity and blank artwork produce no invalid candidate") {
		val item = BaseItemDto(
			id = UUID.randomUUID(),
			type = BaseItemKind.MOVIE,
			backdropImageTags = listOf(" ", ""),
			parentBackdropImageTags = listOf("orphan-tag"),
		)
		krispyHeroBackdropCandidates(item).shouldBe(emptyList())
	}
})
