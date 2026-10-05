package org.jellyfin.androidtv.ui.itemdetail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.ProvideKrispyEnhancedMetadataStyle
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRowRuntime
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowCommunityRating
import org.jellyfin.androidtv.ui.browsing.composable.inforow.InfoRowItem
import org.jellyfin.androidtv.ui.home.krispyHomeStudio
import org.jellyfin.androidtv.ui.home.krispyHomeYearLabel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks

/** Non-focusable metadata; the existing buttons and rows retain remote navigation. */
class KrispyDetailsMetadataView(context: Context) : AbstractComposeView(context) {
	var item by mutableStateOf<BaseItemDto?>(null)

	init {
		isFocusable = false
		descendantFocusability = FOCUS_BLOCK_DESCENDANTS
	}

	@Composable
	override fun Content() {
		val current = item ?: return
		ProvideKrispyEnhancedMetadataStyle {
			Metadata(current)
		}
	}
}

@Composable
private fun Metadata(current: BaseItemDto) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
		current.communityRating?.let { MetadataPart { InfoRowCommunityRating(it / 10f) } }
		current.krispyHomeYearLabel(stringResource(R.string.krispy_home_present))?.let {
			MetadataPart { InfoRowItem(contentDescription = null) { Text(it) } }
		}
		current.krispyHomeStudio()?.let {
			MetadataPart { InfoRowItem(contentDescription = null) { Text(stringResource(R.string.krispy_home_on_studio, it)) } }
		}
		current.runTimeTicks?.takeIf { it > 0 }?.ticks?.let { MetadataPart { BaseItemInfoRowRuntime(it) } }
		current.officialRating?.takeIf(String::isNotBlank)?.let {
			MetadataPart { InfoRowItem(contentDescription = null) { Text(it) } }
		}
	}
}

@Composable
private fun MetadataPart(content: @Composable () -> Unit) {
	Box(Modifier.height(IntrinsicSize.Min)) { content() }
}
