package org.jellyfin.androidtv.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

internal data class KrispyApkIdentity(
	val packageName: String,
	val versionCode: Long,
	val versionName: String?,
	val certificates: Set<String>,
	val minimumSdk: Int? = null,
)

internal fun validateKrispyApkIdentity(
	archive: KrispyApkIdentity,
	installed: KrispyApkIdentity,
	metadata: KrispyUpdateMetadata,
	deviceSdk: Int,
) {
	val valid = archive.packageName == installed.packageName &&
		archive.versionCode == metadata.versionCode && archive.versionCode > installed.versionCode &&
		archive.versionName == metadata.versionName &&
		archive.certificates.isNotEmpty() && archive.certificates == installed.certificates &&
		(archive.minimumSdk == null || archive.minimumSdk <= deviceSdk)
	if (!valid) throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
}

class KrispyApkVerifier(context: Context) {
	private val application = context.applicationContext

	@Suppress("DEPRECATION")
	fun verify(file: File, metadata: KrispyUpdateMetadata) {
		val manager = application.packageManager
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
		else PackageManager.GET_SIGNATURES
		val archive = manager.getPackageArchiveInfo(file.absolutePath, flags)
			?: throw KrispyUpdateException(KrispyUpdateMessage.INVALID_APK)
		val installed = manager.getPackageInfo(application.packageName, flags)
		validateKrispyApkIdentity(archive.identity(), installed.identity(), metadata, Build.VERSION.SDK_INT)
	}

	@Suppress("DEPRECATION")
	private fun PackageInfo.identity(): KrispyApkIdentity {
		val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) signingInfo?.apkContentsSigners
		else signatures
		val certificates = signers?.map {
			MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toUpdateHex()
		}?.toSet().orEmpty()
		return KrispyApkIdentity(
			packageName = packageName,
			versionCode = PackageInfoCompat.getLongVersionCode(this),
			versionName = versionName,
			certificates = certificates,
			minimumSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) applicationInfo?.minSdkVersion else null,
		)
	}
}
