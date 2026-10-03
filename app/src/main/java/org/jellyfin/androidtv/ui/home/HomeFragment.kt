package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.update.KrispyUpdateRepository
import org.jellyfin.design.Tokens
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val HERO_HEIGHT_FRACTION = 0.32f

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()
	private val krispyHomeViewModel by viewModel<KrispyHomeViewModel>()
	private val krispyUpdates by inject<KrispyUpdateRepository>()
	private var homeRowsFragment: HomeRowsFragment? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val rowsFocusRequester = remember { FocusRequester() }

		val enhancedHomeEnabled by krispyHomeViewModel.enabled.collectAsStateWithLifecycle(viewLifecycleOwner)
		LaunchedEffect(rowsFocusRequester, enhancedHomeEnabled) { rowsFocusRequester.requestFocus() }
		val hero by krispyHomeViewModel.hero.collectAsStateWithLifecycle(viewLifecycleOwner)
		val updates by krispyUpdates.state.collectAsStateWithLifecycle(viewLifecycleOwner)

		BoxWithConstraints(Modifier.fillMaxSize()) {
			val heroHeight = maxHeight * HERO_HEIGHT_FRACTION
			if (enhancedHomeEnabled) KrispyHomeHeroBackdrop(hero.backdrop, Modifier.fillMaxSize())
			Column {
				MainToolbar(MainToolbarActiveButton.Home)
				updates.available?.let { update ->
					Text(
						text = stringResource(R.string.krispy_update_notice, update.metadata.versionName),
						modifier = Modifier.padding(horizontal = Tokens.Space.spaceSm, vertical = Tokens.Space.spaceXs),
					)
				}
				if (enhancedHomeEnabled) KrispyHomeHero(
					state = hero,
					modifier = Modifier.fillMaxWidth().height(heroHeight),
				)

				// Leanback manages focus separately from Compose. Only allow upward exit from the first row.
				// Reset its selection and focus when moving to the toolbar so returning focus remains predictable.
				key(enhancedHomeEnabled) {
					var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
					AndroidFragment<HomeRowsFragment>(
						modifier = Modifier
							.focusGroup()
							.focusRequester(rowsFocusRequester)
							.focusProperties {
								onExit = {
									val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
									if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
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
							homeRowsFragment = fragment
							fragment.onHeroItemSelected = krispyHomeViewModel::onItemFocused
							krispyHomeViewModel.onItemFocused(fragment.selectedItem)
						}
					)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		krispyHomeViewModel.onItemFocused(homeRowsFragment?.selectedItem)
		krispyHomeViewModel.startHome()
		viewLifecycleOwner.lifecycleScope.launch { krispyUpdates.check() }
	}

	override fun onPause() {
		krispyHomeViewModel.stopHome()
		super.onPause()
	}

	override fun onDestroyView() {
		krispyHomeViewModel.stopHome()
		homeRowsFragment?.onHeroItemSelected = null
		homeRowsFragment = null
		super.onDestroyView()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}
