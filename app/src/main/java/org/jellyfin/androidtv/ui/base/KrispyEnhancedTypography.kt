package org.jellyfin.androidtv.ui.base

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jellyfin.androidtv.ui.browsing.composable.inforow.LocalInfoRowTextStyle

/** Text hierarchy shared by the optional enhanced home and detail screens. */
object KrispyEnhancedTypography {
	val captionTypeface: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
	val captionFontFamily = FontFamily(captionTypeface)
	val primaryText = Color(0xE6FFFFFF)
	val secondaryText = Color(0xC7FFFFFF)
	val descriptionText = Color(0xB3FFFFFF)
	val captionSecondaryText = Color(0xA6FFFFFF)
}

@Composable
fun ProvideKrispyEnhancedMetadataStyle(content: @Composable () -> Unit) {
	CompositionLocalProvider(
		LocalInfoRowTextStyle provides TextStyle(
			color = KrispyEnhancedTypography.secondaryText,
			fontWeight = FontWeight.Normal,
		),
		content = content,
	)
}
