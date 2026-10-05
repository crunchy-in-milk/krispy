package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.KrispyEnhancedTypography
import org.jellyfin.androidtv.ui.base.ProvideKrispyEnhancedMetadataStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRowRuntime
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowCommunityRating
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowItem
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowParentalRating
import org.jellyfin.design.Tokens
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks

private const val BACKDROP_TRANSITION_MILLIS = 300
private const val BACKDROP_WIDTH_FRACTION = 0.56f
private const val BACKDROP_TOP_FRACTION = 0.11f
private const val BACKDROP_ASPECT_RATIO = 16f / 9f
private const val GRADIENT_MIDDLE_POINT = 0.7f
private const val GRADIENT_MIDDLE_ALPHA = 0.15f
private const val GRADIENT_BOTTOM_POINT = 0.98f
private const val MAX_OVERVIEW_LINES = 4
private const val HERO_TEXT_WIDTH_FRACTION = 0.56f
private const val COMMUNITY_RATING_SCALE = 10f

@Composable
fun KrispyHomeHeroBackdrop(backdrop: ImageBitmap?, modifier: Modifier = Modifier) {
	val background = JellyfinTheme.colorScheme.background
	BoxWithConstraints(modifier = modifier.background(background)) {
		Crossfade(
			targetState = backdrop,
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = maxHeight * BACKDROP_TOP_FRACTION)
				.fillMaxWidth(BACKDROP_WIDTH_FRACTION)
				.aspectRatio(BACKDROP_ASPECT_RATIO),
			animationSpec = tween(BACKDROP_TRANSITION_MILLIS),
			label = "KrispyHeroBackdrop",
		) { image ->
			Box(Modifier.fillMaxSize()) {
				if (image != null) Image(
					bitmap = image,
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
				Canvas(Modifier.fillMaxSize()) {
					drawRect(
						Brush.verticalGradient(
							0f to background,
							0.14f to background.copy(alpha = GRADIENT_MIDDLE_ALPHA),
							GRADIENT_MIDDLE_POINT to background.copy(alpha = GRADIENT_MIDDLE_ALPHA),
							GRADIENT_BOTTOM_POINT to background,
							1f to background,
							endY = size.height,
						)
					)
				}
			}
		}
		Canvas(Modifier.fillMaxSize()) {
			drawRect(
				Brush.horizontalGradient(
					0f to background,
					(1f - BACKDROP_WIDTH_FRACTION) to background,
					HERO_TEXT_WIDTH_FRACTION to background.copy(alpha = 0.7f),
					0.64f to Color.Transparent,
					1f to Color.Transparent,
					endX = size.width,
				)
			)
		}
	}
}

/** A non-focusable metadata header; the toolbar and Leanback rows keep their existing focus tree. */
@Composable
fun KrispyHomeHero(state: KrispyHomeHeroState, modifier: Modifier = Modifier) {
	Box(modifier.padding(horizontal = Tokens.Space.space3xl, vertical = Tokens.Space.spaceSm)) {
		val titleSize = Tokens.Typography.typographyFontSize2xl
		Column(
			modifier = Modifier.fillMaxWidth(HERO_TEXT_WIDTH_FRACTION),
			verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceSm),
		) {
			state.title?.takeIf(String::isNotBlank)?.let { title ->
				Text(
					text = title,
					style = JellyfinTheme.typography.listHeader.copy(
						fontSize = titleSize.value.sp,
						fontWeight = FontWeight.Normal,
						lineHeight = (titleSize.value * 1.15f).sp,
					),
					color = JellyfinTheme.colorScheme.onBackground,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			ProvideKrispyEnhancedMetadataStyle {
				state.item?.let { KrispyHomeHeroMetadata(it) }
			}
			state.item?.overview?.takeIf(String::isNotBlank)?.let { overview ->
				Text(
					text = overview,
					style = JellyfinTheme.typography.listCaption.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
					color = KrispyEnhancedTypography.descriptionText,
					maxLines = MAX_OVERVIEW_LINES,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)
			}
		}
	}
}

@Composable
private fun KrispyHomeHeroMetadata(item: BaseItemDto) {
	Row(
		modifier = Modifier.height(IntrinsicSize.Min),
		horizontalArrangement = Arrangement.spacedBy(Tokens.Space.spaceSm),
		verticalAlignment = Alignment.CenterVertically,
	) {
		item.communityRating?.let { InfoRowCommunityRating(it / COMMUNITY_RATING_SCALE) }
		item.krispyHomeYearLabel(stringResource(R.string.krispy_home_present))?.let { year ->
			InfoRowItem(contentDescription = null) { Text(year) }
		}
		item.krispyHomeStudio()?.let { studio ->
			InfoRowItem(contentDescription = null) {
				Text(stringResource(R.string.krispy_home_on_studio, studio), maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
		item.runTimeTicks?.takeIf { it > 0 }?.ticks?.let { BaseItemInfoRowRuntime(it) }
		item.officialRating?.takeIf(String::isNotBlank)?.let { InfoRowParentalRating(it) }
	}
}
