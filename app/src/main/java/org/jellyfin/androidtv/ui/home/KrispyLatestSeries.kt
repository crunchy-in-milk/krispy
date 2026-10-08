package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

internal fun latestEpisodeSeriesIds(items: Collection<BaseItemDto>): Set<UUID> = items
	.asSequence()
	.filter { it.type == BaseItemKind.EPISODE }
	.mapNotNull { it.seriesId }
	.toCollection(linkedSetOf())

internal fun promoteLatestEpisodesToSeries(
	items: Collection<BaseItemDto>,
	seriesById: Map<UUID, BaseItemDto>,
): List<BaseItemDto> {
	val availableSeries = items
		.asSequence()
		.filter { it.type == BaseItemKind.SERIES }
		.associateBy { it.id }
		.plus(seriesById)
	val seenSeries = mutableSetOf<UUID>()
	return items.mapNotNull { item ->
		val seriesId = when (item.type) {
			BaseItemKind.EPISODE -> item.seriesId
			BaseItemKind.SERIES -> item.id
			else -> null
		}
		val promoted = if (item.type == BaseItemKind.EPISODE) seriesId?.let(availableSeries::get) ?: item else item
		if (seriesId == null || seenSeries.add(seriesId)) promoted else null
	}
}
