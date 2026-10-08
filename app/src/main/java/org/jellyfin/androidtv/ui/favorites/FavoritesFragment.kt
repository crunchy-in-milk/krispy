package org.jellyfin.androidtv.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.home.KrispyHomeHero
import org.jellyfin.androidtv.ui.home.KrispyHomeHeroBackdrop
import org.jellyfin.androidtv.ui.home.KrispyHomeViewModel
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val HERO_HEIGHT_FRACTION = 0.32f

class FavoritesFragment : Fragment() {
	private val navigationRepository by inject<NavigationRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val heroViewModel by viewModel<KrispyHomeViewModel>()
	private var rowsFragment: FavoritesRowsFragment? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }
		val enhancedHomeEnabled by heroViewModel.enabled.collectAsStateWithLifecycle(viewLifecycleOwner)
		val hero by heroViewModel.hero.collectAsStateWithLifecycle(viewLifecycleOwner)

		LaunchedEffect(Unit) {
			userPreferences.observeKrispyFavoritesTabEnabled().collect { enabled ->
				if (!enabled) navigationRepository.navigate(Destinations.home, replace = true)
			}
		}
		LaunchedEffect(rowsFocusRequester, enhancedHomeEnabled) { rowsFocusRequester.requestFocus() }

		BoxWithConstraints(Modifier.fillMaxSize()) {
			val heroHeight = maxHeight * HERO_HEIGHT_FRACTION
			if (enhancedHomeEnabled) KrispyHomeHeroBackdrop(hero.backdrop, modifier = Modifier.fillMaxSize())
			Column {
				MainToolbar(MainToolbarActiveButton.Favorites)
				if (enhancedHomeEnabled) KrispyHomeHero(
					state = hero,
					modifier = Modifier.fillMaxWidth().height(heroHeight),
				)

				key(enhancedHomeEnabled) {
					var rowsSupportFragment by remember { mutableStateOf<FavoritesRowsFragment?>(null) }
					AndroidFragment<FavoritesRowsFragment>(
						modifier = Modifier
							.focusGroup()
							.focusRequester(rowsFocusRequester)
							.focusProperties {
								onExit = {
									val firstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
									if (requestedFocusDirection != FocusDirection.Up || !firstRowSelected) {
										cancelFocusChange()
									} else {
										rowsSupportFragment?.selectedPosition = 0
										rowsSupportFragment?.verticalGridView?.clearFocus()
									}
								}
							}
							.fillMaxSize(),
						onUpdate = { fragment ->
							rowsSupportFragment = fragment
							rowsFragment = fragment
							fragment.onHeroItemSelected = heroViewModel::onItemFocused
							heroViewModel.onItemFocused(fragment.selectedItem)
						},
					)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		heroViewModel.onItemFocused(rowsFragment?.selectedItem)
		heroViewModel.startHome()
	}

	override fun onPause() {
		heroViewModel.stopHome()
		super.onPause()
	}

	override fun onDestroyView() {
		heroViewModel.stopHome()
		rowsFragment?.onHeroItemSelected = null
		rowsFragment = null
		super.onDestroyView()
	}
}
