package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.querying.GetUserViewsRequest
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CardPresenterStyle
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class HomeFragmentViewsRow(
	val small: Boolean,
	private val enhancedHome: Boolean = false,
) : HomeFragmentRow {
	private companion object {
		val smallCardPresenter = CardPresenter(true, 75)
		val largeCardPresenter = CardPresenter(true, 126)
	}

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val presenter = if (enhancedHome) {
			CardPresenter(
				false,
				ImageType.THUMB,
				if (small) 68 else 112,
				true,
				CardPresenterStyle.ENHANCED_HOME_MEDIA,
			)
		} else if (small) smallCardPresenter else largeCardPresenter
		val rowAdapter = ItemRowAdapter(context, GetUserViewsRequest, enhancedHome, presenter, rowsAdapter)

		val header = HeaderItem(context.getString(if (enhancedHome) R.string.krispy_home_media else R.string.lbl_my_media))
		val row = ListRow(header, rowAdapter)
		rowAdapter.setRow(row)
		rowAdapter.Retrieve()
		rowsAdapter.add(row)
	}
}
