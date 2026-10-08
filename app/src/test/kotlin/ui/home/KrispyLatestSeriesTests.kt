package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

private fun latestItem(type: BaseItemKind, id: UUID = UUID.randomUUID(), seriesId: UUID? = null, name: String? = null) = BaseItemDto(
	id = id,
	type = type,
	seriesId = seriesId,
	name = name,
)

class KrispyLatestSeriesTests : FunSpec({
	test("singleton latest episodes are promoted to their series") {
		val seriesId = UUID.randomUUID()
		val episode = latestItem(BaseItemKind.EPISODE, seriesId = seriesId, name = "New episode")
		val series = latestItem(BaseItemKind.SERIES, id = seriesId, name = "The series")

		promoteLatestEpisodesToSeries(listOf(episode), mapOf(seriesId to series)).shouldBe(listOf(series))
	}

	test("promotion preserves latest order and removes duplicate series") {
		val firstSeriesId = UUID.randomUUID()
		val secondSeriesId = UUID.randomUUID()
		val firstSeries = latestItem(BaseItemKind.SERIES, firstSeriesId, name = "First")
		val secondSeries = latestItem(BaseItemKind.SERIES, secondSeriesId, name = "Second")
		val movie = latestItem(BaseItemKind.MOVIE, name = "Movie")
		val items = listOf(
			latestItem(BaseItemKind.EPISODE, seriesId = firstSeriesId),
			movie,
			firstSeries,
			latestItem(BaseItemKind.EPISODE, seriesId = secondSeriesId),
		)

		promoteLatestEpisodesToSeries(items, mapOf(firstSeriesId to firstSeries, secondSeriesId to secondSeries))
			.shouldBe(listOf(firstSeries, movie, secondSeries))
	}

	test("missing series data safely retains the original episode") {
		val episode = latestItem(BaseItemKind.EPISODE, seriesId = UUID.randomUUID())
		promoteLatestEpisodesToSeries(listOf(episode), emptyMap()).shouldBe(listOf(episode))
	}

	test("a series already in the latest response promotes and deduplicates its episode") {
		val seriesId = UUID.randomUUID()
		val episode = latestItem(BaseItemKind.EPISODE, seriesId = seriesId)
		val series = latestItem(BaseItemKind.SERIES, id = seriesId)
		promoteLatestEpisodesToSeries(listOf(episode, series), emptyMap()).shouldBe(listOf(series))
	}

	test("series lookup ids are unique and preserve first appearance") {
		val first = UUID.randomUUID()
		val second = UUID.randomUUID()
		latestEpisodeSeriesIds(listOf(
			latestItem(BaseItemKind.EPISODE, seriesId = first),
			latestItem(BaseItemKind.MOVIE),
			latestItem(BaseItemKind.EPISODE, seriesId = first),
			latestItem(BaseItemKind.EPISODE, seriesId = second),
			latestItem(BaseItemKind.EPISODE),
		)).shouldBe(linkedSetOf(first, second))
	}
})
