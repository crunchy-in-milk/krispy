package org.jellyfin.androidtv.ui.presentation

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.DetailRowView
import org.jellyfin.androidtv.ui.itemdetail.MyDetailsOverviewRow
import org.jellyfin.androidtv.ui.itemdetail.KrispyDetailsMetadataView
import org.jellyfin.androidtv.ui.itemdetail.krispyDetailsGenres
import org.jellyfin.androidtv.util.InfoLayoutHelper
import org.jellyfin.androidtv.util.MarkdownRenderer
import org.jellyfin.sdk.model.api.BaseItemKind

class MyDetailsOverviewRowPresenter @JvmOverloads constructor(
	private val markdownRenderer: MarkdownRenderer,
	private val enhancedDetails: Boolean = false,
) : RowPresenter() {
	class ViewHolder(
		private val detailRowView: DetailRowView,
		private val markdownRenderer: MarkdownRenderer,
		private val enhancedDetails: Boolean,
	) : RowPresenter.ViewHolder(detailRowView) {
		private val binding get() = detailRowView.binding

		fun setItem(row: MyDetailsOverviewRow) {
			setTitle(row.item.name)

			if (enhancedDetails) {
				val metadata = binding.fdMainInfoRow.getChildAt(0) as? KrispyDetailsMetadataView
					?: KrispyDetailsMetadataView(view.context).also { binding.fdMainInfoRow.addView(it) }
				metadata.item = row.item
				val seasons = row.item.childCount?.takeIf { it > 0 && row.item.type == BaseItemKind.SERIES }
					?.let { view.resources.getQuantityString(R.plurals.krispy_detail_seasons, it, it) }
				binding.fdGenreRow.text = row.item.krispyDetailsGenres(seasons)
			} else {
				InfoLayoutHelper.addInfoRow(view.context, row.item, row.item.mediaSources?.getOrNull(row.selectedMediaSourceIndex), binding.fdMainInfoRow, false)
				binding.fdGenreRow.text = row.item.genres?.joinToString(" / ")
			}

			binding.infoTitle1.text = row.infoItem1?.label
			binding.infoValue1.text = row.infoItem1?.value

			binding.infoTitle2.text = row.infoItem2?.label
			binding.infoValue2.text = row.infoItem2?.value

			binding.infoTitle3.text = row.infoItem3?.label
			binding.infoValue3.text = row.infoItem3?.value

			binding.mainImage.load(row.imageDrawable, null, null, 1.0, 0)

			setSummary(row.summary)

			if (row.item.type == BaseItemKind.PERSON) {
				binding.fdSummaryText.maxLines = 9
				binding.fdGenreRow.isVisible = false
			}

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)

				if (enhancedDetails) {
					button.binding.imageButton.setBackgroundResource(R.drawable.krispy_detail_button_background)
					button.binding.imageButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
					button.binding.label.setTextColor(Color.WHITE)
					button.binding.label.alpha = 0.9f
				}
				binding.fdButtonRow.addView(button)
			}
		}

		fun setTitle(title: String?) {
			binding.fdTitle.text = title
		}

		fun setSummary(summary: String?) {
			binding.fdSummaryText.text = summary?.let { markdownRenderer.toMarkdownSpanned(it) }
		}

		fun setInfoValue3(text: String?) {
			binding.infoValue3.text = text
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailRowView(parent.context, enhancedDetails = enhancedDetails)
		viewHolder = ViewHolder(view, markdownRenderer, enhancedDetails)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is MyDetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
