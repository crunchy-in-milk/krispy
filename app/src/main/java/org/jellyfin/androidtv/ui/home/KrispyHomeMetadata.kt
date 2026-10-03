package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SeriesStatus

internal fun BaseItemDto.krispyHomeYearLabel(presentLabel: String): String? {
	val startYear = productionYear ?: premiereDate?.year ?: return null
	if (type != BaseItemKind.SERIES) return startYear.toString()

	val endYear = endDate?.year
	return when {
		status?.let(SeriesStatus::fromNameOrNull) == SeriesStatus.CONTINUING -> "$startYear - $presentLabel"
		endYear != null && endYear > startYear -> "$startYear - $endYear"
		else -> startYear.toString()
	}
}

internal fun BaseItemDto.krispyHomeStudio(): String? =
	seriesStudio?.trim()?.takeIf(String::isNotEmpty)
		?: studios.orEmpty().asSequence().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull()

internal fun BaseItemDto.krispyHomeCardSubtitle(): String? = listOfNotNull(
	(productionYear ?: premiereDate?.year)?.toString(),
	officialRating?.trim()?.takeIf(String::isNotEmpty),
).joinToString(" • ").takeIf(String::isNotEmpty)
