package org.jellyfin.androidtv.ui.artwork

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jellyfin.androidtv.ui.base.BaseScreen
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class KrispyArtworkFragment : Fragment() {
	private val model by viewModel<KrispyArtworkViewModel>()
	private val navigation by inject<NavigationRepository>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		model.load(requireNotNull(arguments?.getString("ItemId")).toUUID())
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = content {
		BaseScreen {
			KrispyArtworkScreen(model.state.collectAsStateWithLifecycle().value, model) { navigation.goBack() }
		}
	}
}
