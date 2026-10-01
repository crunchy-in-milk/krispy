package org.jellyfin.androidtv.ui.settings.screen.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.update.KrispyUpdateInstaller
import org.jellyfin.androidtv.update.KrispyUpdateMessage
import org.jellyfin.androidtv.update.KrispyUpdateRepository
import org.jellyfin.androidtv.update.KrispyUpdateState
import org.koin.compose.koinInject

@Composable
fun SettingsUpdatesScreen() {
	val updates = koinInject<KrispyUpdateRepository>()
	val state by updates.state.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	var downloadJob by remember { mutableStateOf<Job?>(null) }
	LaunchedEffect(updates) { updates.check() }
	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.app_name).uppercase()) },
				headingContent = { Text(stringResource(R.string.krispy_updates)) },
				captionContent = { Text(BuildConfig.VERSION_NAME) },
			)
		}
		item { UpdateCheckButton(state) { scope.launch { updates.check(force = true) } } }
		if (state.available != null && state.readyApk == null) item {
			UpdateDownloadButton(state) { downloadJob = scope.launch { updates.download() } }
		}
		if (state.downloading) item {
			ListButton(
				headingContent = { Text(stringResource(R.string.krispy_update_cancel)) },
				onClick = { downloadJob?.cancel() },
				modifier = Modifier.focusKey("cancel-update"),
			)
		}
		if (state.readyApk != null) item { InstallUpdateControl(updates, state.busy) }
	}
}

@Composable
private fun InstallUpdateControl(updates: KrispyUpdateRepository, busy: Boolean) {
	val installer = koinInject<KrispyUpdateInstaller>()
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val scope = rememberCoroutineScope()
	var installing by remember { mutableStateOf(false) }
	var canInstall by remember { mutableStateOf(installer.canInstall(context)) }
	DisposableEffect(lifecycleOwner, context) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) canInstall = installer.canInstall(context)
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
	UpdateInstallButton(canInstall, busy || installing) {
		if (!canInstall) {
			runCatching { installer.openInstallSettings(context) }.onFailure { updates.installerUnavailable() }
		} else scope.launch {
			installing = true
			try {
				val apk = updates.verifiedApk() ?: return@launch
				canInstall = installer.canInstall(context)
				if (canInstall) runCatching { installer.install(context, apk) }.onFailure { updates.installerUnavailable() }
			} finally {
				installing = false
			}
		}
	}
}

@Composable
private fun UpdateCheckButton(state: KrispyUpdateState, check: () -> Unit) {
	ListButton(
		headingContent = {
			Text(stringResource(if (state.checking) R.string.krispy_checking_updates else R.string.krispy_check_updates))
		},
		captionContent = { Text(updateCaption(state)) },
		onClick = check,
		enabled = !state.busy,
		modifier = Modifier.focusKey("check-updates"),
	)
}

@Composable
private fun UpdateDownloadButton(state: KrispyUpdateState, download: () -> Unit) {
	ListButton(
		headingContent = { Text(stringResource(R.string.krispy_update_download)) },
		captionContent = {
			if (state.downloading) Text(stringResource(R.string.krispy_update_downloading, state.progress))
			else Text(stringResource(R.string.krispy_update_available, state.available?.metadata?.versionName.orEmpty()))
		},
		onClick = download,
		enabled = !state.busy,
		modifier = Modifier.focusKey("download-update"),
	)
}

@Composable
private fun UpdateInstallButton(canInstall: Boolean, busy: Boolean, install: () -> Unit) {
	ListButton(
		headingContent = { Text(stringResource(if (canInstall) R.string.krispy_update_install else R.string.krispy_update_allow)) },
		captionContent = {
			Text(stringResource(if (canInstall) R.string.krispy_update_install_description else R.string.krispy_update_allow_description))
		},
		onClick = install,
		enabled = !busy,
		modifier = Modifier.focusKey("install-update"),
	)
}

@Composable
private fun updateCaption(state: KrispyUpdateState): String = when (state.message) {
	KrispyUpdateMessage.UP_TO_DATE -> stringResource(R.string.krispy_update_current)
	KrispyUpdateMessage.NO_RELEASE -> stringResource(R.string.krispy_update_no_release)
	KrispyUpdateMessage.CHECK_FAILED -> stringResource(R.string.krispy_update_check_failed)
	KrispyUpdateMessage.INVALID_RELEASE -> stringResource(R.string.krispy_update_invalid_release)
	KrispyUpdateMessage.DOWNLOAD_FAILED -> stringResource(R.string.krispy_update_download_failed)
	KrispyUpdateMessage.INVALID_APK -> stringResource(R.string.krispy_update_invalid_apk)
	KrispyUpdateMessage.INSTALL_UNAVAILABLE -> stringResource(R.string.krispy_update_install_unavailable)
	null -> state.available?.let { stringResource(R.string.krispy_update_available, it.metadata.versionName) }
		?: stringResource(R.string.krispy_updates_description)
}
