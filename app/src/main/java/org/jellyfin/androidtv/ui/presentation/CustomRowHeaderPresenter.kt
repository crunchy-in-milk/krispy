package org.jellyfin.androidtv.ui.presentation

import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter
import org.jellyfin.androidtv.ui.base.KrispyEnhancedTypography

class CustomRowHeaderPresenter(private val enhancedTypography: Boolean = false) : RowHeaderPresenter() {
	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		super.onBindViewHolder(viewHolder, item)
		if (enhancedTypography) {
			viewHolder.view.findViewById<TextView>(androidx.leanback.R.id.row_header)?.apply {
				typeface = KrispyEnhancedTypography.captionTypeface
				setTextColor(android.graphics.Color.rgb(217, 217, 217))
			}
		}
	}

	override fun onSelectLevelChanged(holder: ViewHolder) = Unit
}
