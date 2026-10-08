package org.jellyfin.androidtv.ui.presentation

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.leanback.widget.Presenter
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.KrispyEnhancedTypography
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.item.ItemCard
import org.jellyfin.androidtv.ui.composable.item.ItemCardBaseItemOverlay
import org.jellyfin.androidtv.ui.composable.item.ItemPreview
import org.jellyfin.androidtv.ui.home.krispyHomeCardSubtitle
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowType
import org.jellyfin.androidtv.ui.itemhandling.GridButtonBaseRowItem
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.getActivity
import org.jellyfin.design.Tokens
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.compose.koinInject

enum class CardPresenterStyle { DEFAULT, ENHANCED_HOME, ENHANCED_HOME_MEDIA, ENHANCED_DETAILS }

class CardPresenter(
	val showInfo: Boolean,
	val imageType: ImageType,
	val staticHeight: Int,
	val uniformAspect: Boolean,
	private val style: CardPresenterStyle,
) : Presenter() {
	constructor(showInfo: Boolean, imageType: ImageType, staticHeight: Int, uniformAspect: Boolean) :
		this(showInfo, imageType, staticHeight, uniformAspect, CardPresenterStyle.DEFAULT)
	constructor(showInfo: Boolean, imageType: ImageType, staticHeight: Int) : this(showInfo, imageType, staticHeight, false)
	constructor(showInfo: Boolean, staticHeight: Int) : this(showInfo, ImageType.POSTER, staticHeight)
	constructor(showInfo: Boolean, staticHeight: Int, style: CardPresenterStyle) : this(showInfo, ImageType.POSTER, staticHeight, false, style)
	constructor(showInfo: Boolean) : this(showInfo, 150)
	constructor() : this(true)
	constructor(style: CardPresenterStyle) : this(true, ImageType.POSTER, 150, false, style)

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = ComposeView(parent.context).apply {
			setParentCompositionContext(parent.findViewTreeCompositionContext())
			setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
			setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
			isFocusable = true
			isFocusableInTouchMode = true

			setOnLongClickListener {
				context.getActivity()?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU)) ?: false
			}
		}

		return CardViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		if (viewHolder !is CardViewHolder) return
		if (item !is BaseRowItem) return

		viewHolder.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		if (viewHolder !is CardViewHolder) return

		viewHolder.unbind()
	}

	private inner class CardViewHolder(composeView: ComposeView) : ViewHolder(composeView) {
		private val _item = MutableStateFlow<BaseRowItem?>(null)
		private val _focused = MutableStateFlow(false)

		init {
			composeView.setContent {
				val item by _item.collectAsState()
				val focused by _focused.collectAsState()

				CardViewHolderContent(
					item = item,
					focused = focused,
					showInfo = showInfo,
					imageType = imageType,
					staticHeight = staticHeight,
					uniformAspect = uniformAspect,
					style = style,
				)
			}

			_focused.value = view.isFocused
			composeView.onFocusChangeListener = { _, focused -> _focused.value = focused }
		}

		fun bind(item: BaseRowItem) {
			_item.value = item
			_focused.value = view.isFocused
		}

		fun unbind() {
			_item.value = null
			_focused.value = false
		}
	}
}

private data class BaseRowItemDisplayConfig(
	val image: JellyfinImage?,
	val iconRes: Int,
	val aspectRatio: Float,
	val overrideShowInfo: Boolean? = null,
	val scaleType: ImageView.ScaleType? = null,
)

private fun BaseRowItem.getDisplayConfig(imageType: ImageType, uniformAspect: Boolean): BaseRowItemDisplayConfig = when (baseRowType) {
	BaseRowType.BaseItem -> {
		val preferSeriesPoster = this is BaseItemDtoBaseRowItem && preferSeriesPoster
		val primaryAspectRatio = baseItem?.primaryImageAspectRatio?.toFloat()
		val defaultAspectRatio = when {
			preferParentThumb && (baseItem?.parentThumbItemId != null || baseItem?.seriesThumbImageTag != null) -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			baseItem?.type == BaseItemKind.EPISODE && primaryAspectRatio != null -> primaryAspectRatio
			baseItem?.type == BaseItemKind.EPISODE && (baseItem.parentThumbItemId != null || baseItem.seriesThumbImageTag != null) -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			baseItem?.type == BaseItemKind.USER_VIEW -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			else -> primaryAspectRatio ?: ImageHelper.ASPECT_RATIO_7_9.toFloat()
		}

		val base = BaseRowItemDisplayConfig(
			aspectRatio = when (imageType) {
				ImageType.BANNER -> ImageHelper.ASPECT_RATIO_BANNER.toFloat()
				ImageType.THUMB -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
				else -> defaultAspectRatio
			},
			image = getImage(imageType),
			iconRes = R.drawable.ic_clapperboard,
		)

		when (baseItem?.type) {
			BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM -> base.copy(
				iconRes = R.drawable.ic_music_album,
				aspectRatio = if (uniformAspect || base.aspectRatio < 0.8f) 1f else base.aspectRatio,
			)

			BaseItemKind.PERSON,
			BaseItemKind.MUSIC_ARTIST -> base.copy(
				iconRes = R.drawable.ic_user,
				aspectRatio = if (uniformAspect || base.aspectRatio < 0.8f) 1f else base.aspectRatio,
			)

			BaseItemKind.SEASON, BaseItemKind.SERIES -> base.copy(
				aspectRatio = if (imageType == ImageType.POSTER) ImageHelper.ASPECT_RATIO_2_3.toFloat() else base.aspectRatio,
				iconRes = R.drawable.ic_tv
			)

			BaseItemKind.EPISODE -> when (preferSeriesPoster) {
				true -> base.copy(
					aspectRatio = ImageHelper.ASPECT_RATIO_2_3.toFloat(),
					iconRes = R.drawable.ic_tv
				)

				false -> base.copy(
					aspectRatio = ImageHelper.ASPECT_RATIO_16_9.toFloat(),
					iconRes = R.drawable.ic_tv,
					overrideShowInfo = true,
				)
			}

			BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW -> base.copy(
				aspectRatio = ImageHelper.ASPECT_RATIO_16_9.toFloat(),
				iconRes = when (baseItem.collectionType) {
					CollectionType.TVSHOWS -> R.drawable.ic_tv
					CollectionType.MOVIES -> R.drawable.ic_movie
					CollectionType.LIVETV -> R.drawable.ic_tv_guide
					CollectionType.MUSIC -> R.drawable.ic_music_album
					else -> R.drawable.ic_folder
				},
			)

			BaseItemKind.FOLDER, BaseItemKind.GENRE, BaseItemKind.MUSIC_GENRE -> base.copy(
				iconRes = R.drawable.ic_folder,
			)

			BaseItemKind.PHOTO -> base.copy(
				iconRes = R.drawable.ic_photo
			)

			BaseItemKind.PHOTO_ALBUM, BaseItemKind.PLAYLIST -> base.copy(
				iconRes = R.drawable.ic_folder
			)

			BaseItemKind.MOVIE, BaseItemKind.VIDEO -> base.copy(
				aspectRatio = when (imageType) {
					ImageType.POSTER -> ImageHelper.ASPECT_RATIO_2_3.toFloat()
					else -> base.aspectRatio
				},
				iconRes = R.drawable.ic_clapperboard,
			)

			else -> base
		}
	}

	BaseRowType.LiveTvChannel -> BaseRowItemDisplayConfig(
		aspectRatio = when (imageType) {
			ImageType.BANNER -> ImageHelper.ASPECT_RATIO_BANNER.toFloat()
			ImageType.THUMB -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			else -> baseItem?.primaryImageAspectRatio?.toFloat() ?: 1f
		},
		image = getImage(imageType),
		scaleType = ImageView.ScaleType.FIT_CENTER,
		iconRes = R.drawable.ic_tv,
	)

	BaseRowType.LiveTvProgram -> BaseRowItemDisplayConfig(
		aspectRatio = when (imageType) {
			ImageType.BANNER -> ImageHelper.ASPECT_RATIO_BANNER.toFloat()
			ImageType.THUMB -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			else -> baseItem?.primaryImageAspectRatio?.toFloat() ?: ImageHelper.ASPECT_RATIO_7_9.toFloat()
		},
		image = getImage(imageType),
		iconRes = R.drawable.ic_tv,
		overrideShowInfo = true,
	)

	BaseRowType.LiveTvRecording -> BaseRowItemDisplayConfig(
		aspectRatio = when (imageType) {
			ImageType.BANNER -> ImageHelper.ASPECT_RATIO_BANNER.toFloat()
			ImageType.THUMB -> ImageHelper.ASPECT_RATIO_16_9.toFloat()
			else -> baseItem?.primaryImageAspectRatio?.toFloat() ?: ImageHelper.ASPECT_RATIO_7_9.toFloat()
		},
		image = getImage(imageType),
		iconRes = R.drawable.ic_tv,
	)

	BaseRowType.SeriesTimer -> BaseRowItemDisplayConfig(
		aspectRatio = ImageHelper.ASPECT_RATIO_16_9.toFloat(),
		iconRes = R.drawable.ic_tv_timer,
		image = getImage(imageType),
		overrideShowInfo = true,
	)

	BaseRowType.Person -> BaseRowItemDisplayConfig(
		aspectRatio = ImageHelper.ASPECT_RATIO_7_9.toFloat(),
		image = getImage(imageType),
		iconRes = R.drawable.ic_user,
	)

	BaseRowType.Chapter -> BaseRowItemDisplayConfig(
		aspectRatio = ImageHelper.ASPECT_RATIO_16_9.toFloat(),
		image = getImage(imageType),
		iconRes = R.drawable.ic_clapperboard,
	)

	BaseRowType.GridButton -> BaseRowItemDisplayConfig(
		aspectRatio = ImageHelper.ASPECT_RATIO_7_9.toFloat(),
		image = getImage(imageType),
		iconRes = R.drawable.ic_clapperboard,
	)
}

@Composable
@Stable
private fun CardViewHolderContent(
	item: BaseRowItem?,
	focused: Boolean,
	showInfo: Boolean,
	imageType: ImageType,
	staticHeight: Int,
	uniformAspect: Boolean,
	style: CardPresenterStyle,
) {
	val context = LocalContext.current
	val localDensity = LocalDensity.current
	val enhancedTypography = style != CardPresenterStyle.DEFAULT

	val title = remember(item, context) { item?.getCardName(context) }
	val subtitle = remember(item, context, style) {
		val baseItem = item?.baseItem
		if (style == CardPresenterStyle.ENHANCED_HOME && baseItem?.type in setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)) {
			baseItem?.krispyHomeCardSubtitle()
		} else item?.getSubText(context)
	}
	val displayConfig = remember(item, imageType, uniformAspect) { item?.getDisplayConfig(imageType, uniformAspect) }
	if (item == null || displayConfig == null) return

	val image = displayConfig.image
	val aspectRatio = displayConfig.aspectRatio.takeIf { it >= 0.1f }
		?: image?.aspectRatio?.takeIf { it >= 0.1f } ?: 1f

	val size = when (item.staticHeight) {
		true -> DpSize(staticHeight.dp * aspectRatio, staticHeight.dp)
		false if (aspectRatio > 1f) -> DpSize(130.dp * aspectRatio, 130.dp)
		else -> DpSize(150.dp * aspectRatio, 150.dp)
	}

	val usePreview = displayConfig.overrideShowInfo ?: showInfo
	val enhancedHomeStyle = style == CardPresenterStyle.ENHANCED_HOME || style == CardPresenterStyle.ENHANCED_HOME_MEDIA
	val mediaStyle = style == CardPresenterStyle.ENHANCED_HOME_MEDIA
	val cardShape = when {
		mediaStyle -> RoundedCornerShape(8.dp)
		enhancedHomeStyle -> JellyfinTheme.shapes.extraSmall
		else -> JellyfinTheme.shapes.medium
	}
	val focusBorder = if (enhancedHomeStyle && focused) {
		Modifier.border(if (mediaStyle) 3.dp else 2.dp, JellyfinTheme.colorScheme.badge, cardShape)
	} else Modifier
	val mediaBorder = if (mediaStyle && !focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), cardShape) else Modifier
	val focusLift = if (mediaStyle && focused) {
		val lift = with(localDensity) { 3.dp.toPx() }
		val elevation = with(localDensity) { 8.dp.toPx() }
		Modifier.graphicsLayer {
			translationY = -lift
			shadowElevation = elevation
			scaleX = 0.91f
			scaleY = 0.91f
			shape = cardShape
		}
	} else Modifier

	val card = @Composable {
		ItemCard(
			shape = cardShape,
			image = {
				if (mediaStyle) {
					KrispyHomeMediaBand(
						collectionType = item.baseItem?.collectionType,
						iconRes = displayConfig.iconRes,
					)
				} else if (image != null) {
					val api = koinInject<ApiClient>()
					AsyncImage(
						url = image.getUrl(
							api,
							maxWidth = with(localDensity) { size.width.roundToPx() },
							maxHeight = with(localDensity) { size.height.roundToPx() },
						),
						blurHash = image.blurHash,
						aspectRatio = aspectRatio,
						scaleType = displayConfig.scaleType ?: ImageView.ScaleType.CENTER_CROP,
						modifier = Modifier
							.fillMaxSize()
					)
				} else if (item is GridButtonBaseRowItem && item.gridButton.imageRes != null) {
					Image(
						painter = painterResource(item.gridButton.imageRes),
						contentDescription = null,
						modifier = Modifier
							.fillMaxSize()
					)
				} else {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(JellyfinTheme.colorScheme.surface)
					) {
						Image(
							painter = painterResource(displayConfig.iconRes),
							contentDescription = null,
							modifier = Modifier
								.fillMaxSize(0.4f)
								.align(Alignment.Center)
						)
					}
				}
			},
			overlay = {
				if (mediaStyle) {
					KrispyHomeMediaCardOverlay(title = title, subtitle = subtitle, focused = focused)
				} else item.baseItem?.let { baseItem ->
					val showInfo = !usePreview && item.showCardInfoOverlay
					ItemCardBaseItemOverlay(
						item = baseItem,
						footer = {
							if (showInfo && title != null) {
								val focusModifier = if (focused) Modifier.basicMarquee(
									iterations = Int.MAX_VALUE,
									initialDelayMillis = 0,
								) else Modifier

								Box(
									modifier = Modifier
										.fillMaxWidth()
										.background(Tokens.Color.colorBluegrey900.copy(alpha = 0.6f), JellyfinTheme.shapes.extraSmall),
								) {
									Text(
										text = title,
										fontFamily = if (enhancedTypography) KrispyEnhancedTypography.captionFontFamily else null,
										fontWeight = if (enhancedTypography) FontWeight.Normal else null,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										textAlign = TextAlign.Center,
										color = Tokens.Color.colorWhite,
										modifier = Modifier
											.then(focusModifier)
											.padding(Tokens.Space.spaceXs),
									)
								}
							}
						}
					)
				}
			},
			modifier = Modifier
				.size(size)
				.then(focusLift)
				.then(mediaBorder)
				.then(focusBorder)
		)
	}

	if (usePreview && !mediaStyle) {
		val focusModifier = if (focused) Modifier.basicMarquee(
			iterations = Int.MAX_VALUE,
			initialDelayMillis = 0,
		) else Modifier

		ItemPreview(
			card = { card() },
			title = title?.let { text ->
				{
					Text(
						text = text,
						fontSize = if (enhancedTypography) 14.sp else TextUnit.Unspecified,
						fontFamily = if (enhancedTypography) KrispyEnhancedTypography.captionFontFamily else null,
						fontWeight = if (enhancedTypography) FontWeight.Normal else null,
						color = if (enhancedTypography) KrispyEnhancedTypography.primaryText else Color.Unspecified,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						textAlign = if (enhancedTypography) TextAlign.Start else TextAlign.Center,
						modifier = Modifier.then(focusModifier),
					)
				}
			},
			subtitle = subtitle?.let { text ->
				{
					Text(
						text = text,
						fontSize = if (enhancedTypography) 12.sp else TextUnit.Unspecified,
						fontFamily = if (enhancedTypography) KrispyEnhancedTypography.captionFontFamily else null,
						fontWeight = if (enhancedTypography) FontWeight.Normal else null,
						color = if (enhancedTypography) KrispyEnhancedTypography.captionSecondaryText else Color.Unspecified,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						textAlign = if (enhancedTypography) TextAlign.Start else TextAlign.Center,
						modifier = Modifier.then(focusModifier),
					)
				}
			},
		)
	} else {
		card()
	}
}

@Composable
private fun KrispyHomeMediaBand(
	collectionType: CollectionType?,
	iconRes: Int,
) {
	val colors = when (collectionType) {
		CollectionType.TVSHOWS -> listOf(Color(0xFF346C88), Color(0xFF233945))
		CollectionType.LIVETV -> listOf(Color(0xFF7C422F), Color(0xFF39241E))
		CollectionType.MOVIES -> listOf(Color(0xFF816344), Color(0xFF3B2C22))
		CollectionType.PLAYLISTS -> listOf(Color(0xFF72518C), Color(0xFF30223D))
		CollectionType.MUSIC -> listOf(Color(0xFF3F7166), Color(0xFF203B36))
		else -> listOf(Color(0xFF465B69), Color(0xFF25323A))
	}
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Brush.linearGradient(colors))
	) {
		Canvas(Modifier.fillMaxSize()) {
			drawCircle(
				color = Color.White.copy(alpha = 0.16f),
				radius = size.height * 0.76f,
				center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, -size.height * 0.12f),
			)
			drawCircle(
				color = Color.Black.copy(alpha = 0.24f),
				radius = size.height * 0.58f,
				center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 1.16f),
			)
		}
		Image(
			painter = painterResource(iconRes),
			contentDescription = null,
			alpha = 0.62f,
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(14.dp)
				.size(30.dp),
		)
	}
}

@Composable
private fun KrispyHomeMediaCardOverlay(
	title: String?,
	subtitle: String?,
	focused: Boolean,
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.verticalGradient(
					0f to Color.Transparent,
					0.38f to Color.Transparent,
					1f to Color.Black.copy(alpha = 0.88f),
				)
			)
	) {
		if (!title.isNullOrBlank()) {
			Text(
				text = title,
				fontSize = 18.sp,
				fontFamily = KrispyEnhancedTypography.captionFontFamily,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = Tokens.Color.colorWhite,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0) else Modifier)
					.padding(start = 14.dp, end = 14.dp, bottom = if (subtitle.isNullOrBlank()) 11.dp else 25.dp),
			)
		}
		if (!subtitle.isNullOrBlank()) Text(
			text = subtitle,
			fontSize = 11.sp,
			fontFamily = KrispyEnhancedTypography.captionFontFamily,
			fontWeight = FontWeight.Normal,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			color = Color(0xFFC5CCD0),
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
		)
	}
}
