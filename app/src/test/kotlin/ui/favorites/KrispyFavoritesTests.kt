package org.jellyfin.androidtv.ui.favorites

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class KrispyFavoritesTests : FunSpec({
	test("favorites request searches recursively with user data and stable alphabetical sorting") {
		val types = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
		val request = krispyFavoritesRequest(types)

		request.includeItemTypes.shouldBe(types)
		request.filters.shouldBe(setOf(ItemFilter.IS_FAVORITE))
		request.recursive.shouldBe(true)
		request.enableUserData.shouldBe(true)
		request.imageTypeLimit.shouldBe(1)
		request.sortBy.shouldBe(setOf(ItemSortBy.SORT_NAME))
		request.sortOrder.shouldBe(setOf(SortOrder.ASCENDING))
		request.parentId.shouldBe(null)
	}
})
