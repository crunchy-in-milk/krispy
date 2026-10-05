package org.jellyfin.androidtv.ui.artwork

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

private val itemId = UUID.randomUUID()
private fun image(type: ImageType, tag: String, index: Int = 0) = ImageInfo(imageType = type, imageIndex = index, imageTag = tag, size = 1)
private fun backdrops() = (0..2).map { image(ImageType.BACKDROP, "original-$it", it) }

private class FakeArtworkBackend(initial: List<ImageInfo>) : ArtworkBackend {
	val stored = initial.toMutableList()
	private val contents = initial.filter { it.imageType == ImageType.BACKDROP }.associate { it.imageIndex to it.imageTag.orEmpty() }.toMutableMap()
	val writes = mutableListOf<String>()
	var failDownload = false
	var failSwap = false
	var skipSwap = false
	var failDelete = false
	var duplicateDownload = false
	var changeCacheTagsOnSwap = false
	var failFingerprint = false
	var afterSwap: (() -> Unit)? = null
	override suspend fun images(item: UUID) = stored.toList()
	override suspend fun backdropFingerprint(item: UUID, index: Int): String {
		check(!failFingerprint)
		return requireNotNull(contents[index])
	}
	fun corrupt(index: Int) { contents[index] = "changed-elsewhere" }
	override suspend fun download(item: UUID, type: ImageType, url: String) {
		writes += "download"
		check(!failDownload)
		if (duplicateDownload) return
		if (type != ImageType.BACKDROP) stored.removeAll { it.imageType == type }
		val index = stored.count { it.imageType == type }
		stored += image(type, "new", index)
		if (type == ImageType.BACKDROP) contents[index] = "new"
	}
	override suspend fun swap(item: UUID, first: Int, second: Int) {
		writes += "swap:$first:$second"
		check(!failSwap)
		if (skipSwap) return
		val originalFirst = contents[first]
		contents[first] = requireNotNull(contents[second])
		contents[second] = requireNotNull(originalFirst)
		for (i in stored.indices) {
			val current = stored[i]
			if (current.imageType == ImageType.BACKDROP) {
				stored[i] = when (current.imageIndex) {
					first -> current.copy(imageIndex = second)
					second -> current.copy(imageIndex = first)
					else -> current
				}
				if (changeCacheTagsOnSwap && current.imageIndex in listOf(first, second)) {
					stored[i] = stored[i].copy(imageTag = "${stored[i].imageTag}-new-path")
				}
			}
		}
		afterSwap?.invoke()
	}
	override suspend fun delete(item: UUID, type: ImageType, index: Int) {
		writes += "delete:$index"
		check(!failDelete)
		stored.removeAll { it.imageType == type && it.imageIndex == index }
		if (type == ImageType.BACKDROP) contents.remove(index)
	}
}

class KrispyArtworkTests : FunSpec({
	test("artwork editing requires an administrator and a movie or TV item") {
		BaseItemKind.entries.forEach { canEditKrispyArtwork(false, it) shouldBe false }
		listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
			.forEach { canEditKrispyArtwork(true, it) shouldBe true }
		listOf(BaseItemKind.PERSON, BaseItemKind.MUSIC_ALBUM, BaseItemKind.FOLDER, null)
			.forEach { canEditKrispyArtwork(true, it) shouldBe false }
	}

	test("only supported image types appear, with all backdrops and missing image placeholders") {
		val slots = artworkSlots(backdrops().reversed() + image(ImageType.THUMB, "ignored"))
		slots.map { it.type } shouldBe listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.BACKDROP, ImageType.BACKDROP, ImageType.LOGO)
		slots.filter { it.type == ImageType.BACKDROP }.map { it.index } shouldBe listOf(0, 1, 2)
		slots.first().image shouldBe null
		slots.last().image shouldBe null
	}

	test("replacing a backdrop downloads before swapping and deletes only the replaced original") {
		for (target in 0..2) {
			val backend = FakeArtworkBackend(backdrops() + image(ImageType.PRIMARY, "poster"))
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[target]), "https://provider/image") { }
			backend.writes shouldBe listOf("download", "swap:3:$target", "delete:3")
			backend.stored.filter { it.imageType == ImageType.BACKDROP }.sortedBy { it.imageIndex }.map { it.imageTag } shouldBe
				(0..2).map { if (it == target) "new" else "original-$it" }
			backend.stored.single { it.imageType == ImageType.PRIMARY }.imageTag shouldBe "poster"
		}
	}

	test("a failed provider download leaves existing artwork untouched") {
		val backend = FakeArtworkBackend(backdrops()).apply { failDownload = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.stored shouldBe backdrops()
		backend.writes shouldBe listOf("download")
	}

	test("a failed swap retains all originals and does not delete anything") {
		val backend = FakeArtworkBackend(backdrops()).apply { failSwap = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.stored.take(3) shouldBe backdrops()
		backend.writes shouldBe listOf("download", "swap:3:1")
	}

	test("a stale selection is rejected before downloading or deleting artwork") {
		val backend = FakeArtworkBackend(backdrops())
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, image(ImageType.BACKDROP, "stale", 1)), "https://provider/image") { }
		}
		backend.writes shouldBe emptyList()
		shouldThrow<IllegalStateException> { checkArtworkSnapshot(ArtworkSlot(ImageType.PRIMARY, null), listOf(image(ImageType.PRIMARY, "added"))) }
	}

	test("revoked access prevents any write") {
		val backend = FakeArtworkBackend(backdrops())
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { error("Access revoked") }
		}
		backend.writes shouldBe emptyList()
	}

	test("revoked access after download prevents destructive steps") {
		val backend = FakeArtworkBackend(backdrops())
		var checks = 0
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { check(++checks == 1) }
		}
		backend.stored.take(3) shouldBe backdrops()
		backend.writes shouldBe listOf("download")
	}

	test("unexpected artwork after swapping prevents deleting another image") {
		val backend = FakeArtworkBackend(backdrops())
		backend.afterSwap = { backend.corrupt(3) }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.writes shouldBe listOf("download", "swap:3:1")
	}

	test("failed cleanup preserves the replacement and other backdrops") {
		val backend = FakeArtworkBackend(backdrops()).apply { failDelete = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.stored.sortedBy { it.imageIndex }.map { it.imageTag } shouldBe listOf("original-0", "new", "original-2", "original-1")
	}

	test("posters and logos use server replacement without deleting the old image first") {
		for (type in listOf(ImageType.PRIMARY, ImageType.LOGO)) {
			val original = image(type, "old")
			val backend = FakeArtworkBackend(backdrops() + original)
			replaceArtwork(backend, itemId, ArtworkSlot(type, original), "https://provider/image") { }
			backend.writes shouldBe listOf("download")
			backend.stored.filter { it.imageType == ImageType.BACKDROP } shouldBe backdrops()
			backend.stored.single { it.imageType == type }.imageTag shouldBe "new"
		}
	}

	test("adding a missing backdrop requires no swap or deletion") {
		val backend = FakeArtworkBackend(emptyList())
		replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, null), "https://provider/image") { }
		backend.writes shouldBe listOf("download")
		backend.stored.single().imageTag shouldBe "new"
	}

	test("a duplicate backdrop download never deletes existing artwork") {
		val backend = FakeArtworkBackend(backdrops()).apply { duplicateDownload = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.stored shouldBe backdrops()
		backend.writes shouldBe listOf("download")
	}

	test("deletion targets only the selected backdrop and preserves poster and logo") {
		val original = backdrops() + image(ImageType.PRIMARY, "poster") + image(ImageType.LOGO, "logo")
		val backend = FakeArtworkBackend(original)
		deleteArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1])) { }
		backend.writes shouldBe listOf("delete:1")
		backend.stored shouldBe original.filterNot { it.imageType == ImageType.BACKDROP && it.imageIndex == 1 }
	}

	test("poster or logo deletion does not affect other artwork") {
		for (type in listOf(ImageType.PRIMARY, ImageType.LOGO)) {
			val selected = image(type, "selected")
			val backend = FakeArtworkBackend(backdrops() + selected)
			deleteArtwork(backend, itemId, ArtworkSlot(type, selected)) { }
			backend.stored shouldBe backdrops()
		}
	}

	test("a stale selection cannot delete a different image at that index") {
		val backend = FakeArtworkBackend(backdrops())
		shouldThrow<IllegalStateException> {
			deleteArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, image(ImageType.BACKDROP, "stale", 1))) { }
		}
		backend.writes shouldBe emptyList()
		backend.stored shouldBe backdrops()
	}

	test("deletion checks permission immediately before writing") {
		val backend = FakeArtworkBackend(backdrops())
		shouldThrow<IllegalStateException> {
			deleteArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1])) { error("Access revoked") }
		}
		backend.writes shouldBe emptyList()
	}

	test("an empty artwork slot cannot be deleted") {
		val backend = FakeArtworkBackend(emptyList())
		shouldThrow<IllegalArgumentException> { deleteArtwork(backend, itemId, ArtworkSlot(ImageType.LOGO, null)) { } }
		backend.writes shouldBe emptyList()
	}

	test("replacement succeeds when Jellyfin changes cache tags while swapping file contents") {
		val backend = FakeArtworkBackend(backdrops()).apply { changeCacheTagsOnSwap = true }
		replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		backend.writes shouldBe listOf("download", "swap:3:1", "delete:3")
		(0..2).map { backend.backdropFingerprint(itemId, it) } shouldBe listOf("original-0", "new", "original-2")
	}

	test("a failed content verification never deletes any backdrop") {
		val backend = FakeArtworkBackend(backdrops())
		backend.afterSwap = { backend.failFingerprint = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.writes shouldBe listOf("download", "swap:3:1")
		backend.stored.size shouldBe 4
	}

	test("a server swap that does nothing never deletes the newly added or original artwork") {
		val backend = FakeArtworkBackend(backdrops()).apply { skipSwap = true }
		shouldThrow<IllegalStateException> {
			replaceArtwork(backend, itemId, ArtworkSlot(ImageType.BACKDROP, backdrops()[1]), "https://provider/image") { }
		}
		backend.writes shouldBe listOf("download", "swap:3:1")
		backend.stored.take(3) shouldBe backdrops()
	}
})
