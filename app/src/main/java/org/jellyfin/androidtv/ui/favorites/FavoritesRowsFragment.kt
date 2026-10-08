package org.jellyfin.androidtv.ui.favorites

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.browsing.BrowsingUtils
import org.jellyfin.androidtv.ui.home.HomeFragmentBrowseRowDefRow
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CardPresenterStyle
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject

class FavoritesRowsFragment : RowsSupportFragment(), View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()
	private val userPreferences by inject<UserPreferences>()
	private val userRepository by inject<UserRepository>()

	private var currentItem: BaseRowItem? = null
	private var firstResume = true
	internal val selectedItem get() = currentItem
	internal var onHeroItemSelected: ((BaseRowItem?) -> Unit)? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val enhancedHome = userPreferences[UserPreferences.krispyEnhancedHomeEnabled]
		val rowsAdapter = MutableObjectAdapter<Row>(PositionableListRowPresenter(enhancedHome))
		adapter = rowsAdapter
		val cardPresenter = if (enhancedHome) CardPresenter(CardPresenterStyle.ENHANCED_HOME) else CardPresenter()
		val changes = arrayOf(ChangeTriggerType.LibraryUpdated, ChangeTriggerType.FavoriteUpdate)

		val rows = mutableListOf(
			BrowseRowDef(getString(R.string.krispy_favorite_movies), krispyFavoritesRequest(setOf(BaseItemKind.MOVIE)), 60, changes),
			BrowseRowDef(getString(R.string.krispy_favorite_shows), krispyFavoritesRequest(setOf(BaseItemKind.SERIES)), 60, changes),
			BrowseRowDef(getString(R.string.krispy_favorite_episodes), krispyFavoritesRequest(setOf(BaseItemKind.EPISODE)), 60, true, true, changes),
			BrowseRowDef(getString(R.string.krispy_favorite_music), krispyFavoritesRequest(setOf(BaseItemKind.MUSIC_ARTIST, BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO)), 60, changes),
			BrowseRowDef(getString(R.string.krispy_favorite_playlists), krispyFavoritesRequest(setOf(BaseItemKind.PLAYLIST)), 60, changes),
		)
		if (userRepository.currentUser.value?.policy?.enableLiveTvAccess == true) {
			rows.add(BrowseRowDef(getString(R.string.krispy_favorite_channels), BrowsingUtils.createLiveTVChannelsRequest(true)))
		}
		rows.forEach { HomeFragmentBrowseRowDefRow(it).addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter) }

		onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
			if (item !is BaseRowItem || row !is ListRow) return@OnItemViewClickedListener
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}

		onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
			currentItem = item as? BaseRowItem
			val rowAdapter = (row as? ListRow)?.adapter as? ItemRowAdapter
			currentItem?.let { rowAdapter?.loadMoreItemsIfNeeded(rowAdapter.indexOf(it)) }

			if (!enhancedHome) {
				val baseItem = currentItem?.baseItem
				if (baseItem == null) backgroundService.clearBackgrounds() else backgroundService.setBackground(baseItem)
			}
			onHeroItemSelected?.invoke(currentItem)
		}

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>().onEach { refreshRows() }.launchIn(this)
				api.webSocket.subscribe<LibraryChangedMessage>().onEach { refreshRows() }.launchIn(this)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (userPreferences[UserPreferences.krispyEnhancedHomeEnabled]) {
			setAlignment((32 * resources.displayMetrics.density).toInt())
		}
	}

	override fun onResume() {
		super.onResume()
		if (firstResume) firstResume = false else refreshRows()
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	private fun refreshRows() {
		val rowsAdapter = adapter as? MutableObjectAdapter<*> ?: return
		for (row in rowsAdapter) ((row as? ListRow)?.adapter as? ItemRowAdapter)?.Retrieve()
	}
}
