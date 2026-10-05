package org.jellyfin.androidtv.ui.itemdetail

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

fun useKrispyEnhancedDetails(enabled: Boolean, type: BaseItemKind?): Boolean = enabled && when (type) {
	BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE -> true
	else -> false
}

internal fun BaseItemDto.krispyDetailsGenres(seasonLabel: String? = null): String =
	listOfNotNull(
		genres.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) }.joinToString(" / ").takeIf(String::isNotEmpty),
		seasonLabel?.takeIf(String::isNotBlank),
	).joinToString(" • ")
