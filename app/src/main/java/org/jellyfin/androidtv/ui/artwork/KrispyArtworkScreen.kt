package org.jellyfin.androidtv.ui.artwork

import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonBase
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.RemoteImageInfo

private val artworkBlue = Color(0xFF00A4DC)

@Composable
internal fun artworkTypeLabel(type: ImageType): String = stringResource(when (type) {
	ImageType.PRIMARY -> R.string.krispy_artwork_poster
	ImageType.BACKDROP -> R.string.krispy_artwork_backdrop
	else -> R.string.krispy_artwork_logo
})

@Composable
fun KrispyArtworkScreen(state: ArtworkState, model: KrispyArtworkViewModel, onBack: () -> Unit) {
	var actions by remember { mutableStateOf<ArtworkSlot?>(null) }
	var deleting by remember { mutableStateOf<ArtworkSlot?>(null) }
	var replacing by remember { mutableStateOf<RemoteImageInfo?>(null) }
	var providersVisible by remember { mutableStateOf(false) }
	val back = { if (!state.busy) { if (state.target != null) model.backToImages() else onBack() } }
	BackHandler(enabled = state.busy || state.target != null) { back() }

	Column(Modifier.fillMaxSize().background(Color(0xFF202124)).padding(horizontal = 40.dp, vertical = 22.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text(stringResource(if (state.target == null) R.string.krispy_artwork_edit else R.string.krispy_artwork_search),
					color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
				Text(state.item?.name.orEmpty(), color = Color.LightGray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
			if (state.target == null) {
				ArtworkButton(stringResource(R.string.krispy_artwork_refresh), enabled = !state.busy && !state.loading, onClick = { model.reload() })
				Spacer(Modifier.width(12.dp))
			}
			ArtworkButton(stringResource(R.string.krispy_artwork_back), enabled = !state.busy, onClick = back)
		}
		state.error?.let { Text(stringResource(it), color = Color(0xFFFFB4AB), fontSize = 14.sp) }
		state.notice?.let { Text(stringResource(it), color = Color.LightGray, fontSize = 14.sp) }
		if (state.busy) Text(stringResource(R.string.krispy_artwork_saving), color = Color.White)

		val target = state.target
		if (target != null) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
				Text(artworkTypeLabel(target.type), color = Color.White, fontSize = 18.sp)
				ArtworkButton(stringResource(R.string.krispy_artwork_source, state.provider ?: stringResource(R.string.krispy_artwork_all)),
					enabled = !state.busy, onClick = { providersVisible = true })
				ArtworkButton(stringResource(if (state.allLanguages) R.string.krispy_artwork_languages_on else R.string.krispy_artwork_languages_off),
					enabled = !state.busy, onClick = { model.search(target, state.provider, !state.allLanguages) })
			}
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
				ArtworkButton(stringResource(R.string.krispy_artwork_previous), enabled = !state.loading && !state.busy && state.offset > 0,
					onClick = { model.search(target, state.provider, state.allLanguages, (state.offset - 24).coerceAtLeast(0)) })
				ArtworkButton(stringResource(R.string.krispy_artwork_next), enabled = !state.loading && !state.busy && state.offset + 24 < state.total,
					onClick = { model.search(target, state.provider, state.allLanguages, state.offset + 24) })
				if (state.total > 0) Text(stringResource(R.string.krispy_artwork_range, state.offset + 1, minOf(state.offset + 24, state.total), state.total), color = Color.LightGray)
			}
			if (state.loading) Text(stringResource(R.string.krispy_artwork_loading), color = Color.LightGray)
			else if (state.results.isEmpty()) {
				Text(stringResource(R.string.krispy_artwork_no_results), color = Color.LightGray)
				ArtworkButton(stringResource(R.string.krispy_artwork_retry), onClick = { model.search(target, state.provider, state.allLanguages, state.offset) })
			} else key(target.key, state.provider, state.allLanguages, state.offset) {
				ArtworkGrid(state.results, { image, index -> "${image.url}:$index" }) { image, modifier ->
					ArtworkCard(modifier, image.thumbnailUrl?.takeIf(String::isNotBlank) ?: image.url, image.providerName.orEmpty(),
						artworkDimensions(image.width, image.height), remoteArtworkCaption(image), target.type,
						!state.busy) { replacing = image }
				}
			}
		} else if (state.loading) Text(stringResource(R.string.krispy_artwork_loading), color = Color.LightGray)
		else if (state.item == null) ArtworkButton(stringResource(R.string.krispy_artwork_retry), onClick = { model.reload() })
		else {
			Text(stringResource(R.string.krispy_artwork_server_changes), color = Color.LightGray, fontSize = 13.sp)
			ArtworkGrid(state.slots, { slot, _ -> slot.key }) { slot, modifier ->
				val label = artworkTypeLabel(slot.type) + if (slot.type == ImageType.BACKDROP && slot.image != null) " ${slot.index + 1}" else ""
				ArtworkCard(modifier, model.imageUrl(slot), label, artworkDimensions(slot.image?.width, slot.image?.height),
					if (slot.image == null) stringResource(R.string.krispy_artwork_missing) else "", slot.type, !state.busy) { actions = slot }
			}
		}
	}

	actions?.let { slot ->
		ArtworkDialog(onDismiss = { actions = null }) {
			Text(artworkTypeLabel(slot.type), color = Color.White, fontSize = 22.sp)
			ArtworkPreview(model.imageUrl(slot), slot.type, Modifier.fillMaxWidth().height(140.dp))
			ArtworkButton(stringResource(R.string.krispy_artwork_search_new), onClick = { actions = null; model.search(slot) })
			if (slot.image != null) ArtworkButton(stringResource(R.string.krispy_artwork_delete), onClick = { actions = null; deleting = slot })
			ArtworkButton(stringResource(R.string.lbl_cancel), onClick = { actions = null })
		}
	}
	deleting?.let { slot ->
		ArtworkDialog(onDismiss = { deleting = null }) {
			Text(stringResource(R.string.krispy_artwork_delete_confirm, artworkTypeLabel(slot.type)), color = Color.White, fontSize = 20.sp)
			ArtworkPreview(model.imageUrl(slot), slot.type, Modifier.fillMaxWidth().height(160.dp))
			Text(stringResource(R.string.krispy_artwork_server_changes), color = Color.LightGray)
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				ArtworkButton(stringResource(R.string.lbl_cancel), onClick = { deleting = null })
				ArtworkButton(stringResource(R.string.krispy_artwork_delete), onClick = { deleting = null; model.delete(slot) })
			}
		}
	}
	replacing?.let { image ->
		ArtworkDialog(onDismiss = { replacing = null }) {
			Text(stringResource(R.string.krispy_artwork_use_confirm), color = Color.White, fontSize = 20.sp)
			ArtworkPreview(image.url, image.type, Modifier.fillMaxWidth().height(190.dp))
			Text(listOfNotNull(image.providerName, artworkDimensions(image.width, image.height)).filter(String::isNotBlank).joinToString(" • "), color = Color.LightGray)
			Text(stringResource(R.string.krispy_artwork_server_changes), color = Color.LightGray)
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				ArtworkButton(stringResource(R.string.lbl_cancel), onClick = { replacing = null })
				ArtworkButton(stringResource(R.string.krispy_artwork_use), onClick = { replacing = null; model.replace(image) })
			}
		}
	}
	if (providersVisible && state.target != null) ArtworkDialog(onDismiss = { providersVisible = false }) {
		Text(stringResource(R.string.krispy_artwork_provider), color = Color.White, fontSize = 22.sp)
		Column(Modifier.height(230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			val options = listOf<String?>(null) + state.providers.filter { state.target.type in it.supportedImages }.map { it.name }.distinct()
			options.forEach { provider ->
				ArtworkButton(provider ?: stringResource(R.string.krispy_artwork_all), onClick = {
					providersVisible = false; model.search(state.target, provider, state.allLanguages)
				})
			}
		}
	}
}

internal fun artworkDimensions(width: Int?, height: Int?) =
	if (width != null && width > 0 && height != null && height > 0) "$width × $height" else ""

private fun remoteArtworkCaption(image: RemoteImageInfo) = listOfNotNull(
	image.language?.takeIf(String::isNotBlank), image.communityRating?.let { "★ %.1f".format(it) },
	image.voteCount?.let { "($it)" },
).joinToString("  ")

@Composable
private fun ArtworkButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
	Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.colors(focusedContainerColor = artworkBlue, focusedContentColor = Color.White)) { Text(label) }
}

@Composable
private fun ArtworkDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
	DialogBase(visible = true, onDismissRequest = onDismiss) {
		Column(Modifier.width(420.dp).background(Color(0xFF303134), RoundedCornerShape(12.dp)).padding(22.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
	}
}

@Composable
private fun <T> ArtworkGrid(items: List<T>, itemKey: (T, Int) -> String, content: @Composable (T, Modifier) -> Unit) {
	val first = remember { FocusRequester() }
	LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(),
		horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
		itemsIndexed(items, key = { index, item -> itemKey(item, index) }) { index, item ->
			content(item, if (index == 0) Modifier.focusRequester(first) else Modifier)
		}
	}
	LaunchedEffect(items.isNotEmpty()) {
		if (items.isNotEmpty()) {
			withFrameNanos { }
			first.requestFocus()
		}
	}
}

@Composable
private fun ArtworkCard(modifier: Modifier, url: String?, title: String, dimensions: String, caption: String,
	type: ImageType, enabled: Boolean, onClick: () -> Unit) {
	val interaction = remember { MutableInteractionSource() }
	val focused by interaction.collectIsFocusedAsState()
	ButtonBase(onClick = onClick, modifier = modifier.border(if (focused) 3.dp else 0.dp, if (focused) artworkBlue else Color.Transparent, RoundedCornerShape(8.dp)),
		enabled = enabled, shape = RoundedCornerShape(8.dp), interactionSource = interaction,
		colors = ButtonDefaults.colors(containerColor = Color(0xFF303134), focusedContainerColor = Color(0xFF3A3C40), focusedContentColor = Color.White)) {
		Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
			ArtworkPreview(url, type, Modifier.fillMaxWidth().height(140.dp))
			Spacer(Modifier.height(6.dp))
			Text(title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			Text(dimensions, color = Color.LightGray, fontSize = 13.sp, maxLines = 1)
			Text(caption, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
	}
}

@Composable
private fun ArtworkPreview(url: String?, type: ImageType, modifier: Modifier) {
	Box(modifier.background(Color(0xFF252629)), contentAlignment = Alignment.Center) {
		if (url == null) Text(artworkTypeLabel(type), color = Color.Gray, fontSize = 22.sp)
		else AsyncImage(modifier = Modifier.fillMaxSize(), url = url, scaleType = ImageView.ScaleType.FIT_CENTER)
	}
}
