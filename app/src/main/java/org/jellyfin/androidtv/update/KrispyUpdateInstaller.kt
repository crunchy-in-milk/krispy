package org.jellyfin.androidtv.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

class KrispyUpdateFileProvider : FileProvider()

class KrispyUpdateInstaller {
	fun canInstall(context: Context): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

	fun openInstallSettings(context: Context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startActivity(
				Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, ("package:" + context.packageName).toUri())
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
			)
		}
	}

	fun install(context: Context, apk: File) {
		check(canInstall(context))
		val directory = File(context.filesDir, KRISPY_UPDATE_DIRECTORY)
		require(apk.canonicalFile.parentFile == directory.canonicalFile && apk.extension == "apk" && apk.isFile)
		val uri = FileProvider.getUriForFile(context, context.packageName + ".krispy.updates", apk)
		context.startActivity(
			Intent(Intent.ACTION_VIEW)
				.setDataAndType(uri, "application/vnd.android.package-archive")
				.apply { clipData = ClipData.newRawUri("Krispy update", uri) }
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
		)
	}
}
