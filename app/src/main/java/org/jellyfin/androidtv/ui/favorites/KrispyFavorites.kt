package org.jellyfin.androidtv.ui.favorites

import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

internal fun krispyFavoritesRequest(itemTypes: Set<BaseItemKind>) = GetItemsRequest(
	fields = ItemRepository.krispyHomeFields,
	includeItemTypes = itemTypes,
	recursive = true,
	imageTypeLimit = 1,
	enableUserData = true,
	filters = setOf(ItemFilter.IS_FAVORITE),
	sortBy = setOf(ItemSortBy.SORT_NAME),
	sortOrder = setOf(SortOrder.ASCENDING),
)
