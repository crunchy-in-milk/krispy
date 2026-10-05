package org.jellyfin.androidtv.ui.presentation

import android.view.View
import androidx.core.view.isVisible
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.util.Utils

open class CustomListRowPresenter @JvmOverloads constructor(
	private val topPadding: Int? = null,
	private val enhancedTypography: Boolean = false,
) : ListRowPresenter() {
	init {
		headerPresenter = CustomRowHeaderPresenter(enhancedTypography)
	}

	override fun isUsingDefaultShadow() = false

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit

	override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(holder, item)
		if (enhancedTypography && holder is ListRowPresenter.ViewHolder) {
			holder.gridView.setHorizontalSpacing(Utils.convertDpToPixel(holder.view.context, 18))
		}

		val view = holder.view?.parent as? View ?: return
		if (topPadding != null) view.setPadding(view.paddingLeft, topPadding, view.paddingRight, view.paddingBottom)

		// Hide header view when the item doesn't have one
		holder.headerViewHolder.view.isVisible = !(item is ListRow && item.headerItem == null)
	}
}
