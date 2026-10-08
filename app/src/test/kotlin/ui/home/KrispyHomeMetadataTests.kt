package org.jellyfin.androidtv.ui.home

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.NameGuidPair
import java.time.LocalDateTime
import java.util.UUID

class KrispyHomeMetadataTests : FunSpec({
	test("continuing series show a localized present label") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, productionYear = 1997, status = "Continuing")
			.krispyHomeYearLabel("Present").shouldBe("1997 - Present")
	}

	test("ended series show their actual year range") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, productionYear = 2008, status = "Ended", endDate = LocalDateTime.of(2013, 9, 29, 0, 0))
			.krispyHomeYearLabel("Present").shouldBe("2008 - 2013")
	}

	test("unknown status does not invent a present year") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, productionYear = 2008)
			.krispyHomeYearLabel("Present").shouldBe("2008")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, status = "Continuing")
			.krispyHomeYearLabel("Present").shouldBe(null)
	}

	test("movies show one year and can use a premiere date when the production year is missing") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, premiereDate = LocalDateTime.of(2020, 1, 1, 0, 0), status = "Continuing")
			.krispyHomeYearLabel("Present").shouldBe("2020")
	}

	test("series studio is preferred and blank studio entries are skipped") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, seriesStudio = " Comedy Central ", studios = listOf(NameGuidPair(id = UUID.randomUUID(), name = "Other")))
			.krispyHomeStudio().shouldBe("Comedy Central")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, seriesStudio = " ", studios = listOf(NameGuidPair(id = UUID.randomUUID(), name = " "), NameGuidPair(id = UUID.randomUUID(), name = "Pixar")))
			.krispyHomeStudio().shouldBe("Pixar")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE).krispyHomeStudio().shouldBe(null)
	}

	test("poster subtitles omit missing values without stray separators") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SERIES, productionYear = 1997, officialRating = " TV-14 ").krispyHomeCardSubtitle().shouldBe("1997 • TV-14")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, productionYear = 2020, officialRating = " ").krispyHomeCardSubtitle().shouldBe("2020")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, officialRating = "PG").krispyHomeCardSubtitle().shouldBe("PG")
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE).krispyHomeCardSubtitle().shouldBe(null)
	}

	test("library views use the ambient home treatment") {
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.USER_VIEW).isKrispyHomeLibraryView().shouldBe(true)
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.COLLECTION_FOLDER).isKrispyHomeLibraryView().shouldBe(true)
		BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE).isKrispyHomeLibraryView().shouldBe(false)
	}

	test("library card artwork is not added to hero backdrop candidates") {
		val item = BaseItemDto(
			id = UUID.randomUUID(),
			type = BaseItemKind.USER_VIEW,
			backdropImageTags = listOf("backdrop"),
			imageTags = mapOf(ImageType.THUMB to "thumb", ImageType.PRIMARY to "primary"),
		)

		krispyHeroBackdropCandidates(item).map { it.type }.shouldBe(listOf(ImageType.BACKDROP))
	}
})
