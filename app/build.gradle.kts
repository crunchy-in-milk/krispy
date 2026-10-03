import java.util.Properties

plugins {
	alias(libs.plugins.aboutlibraries)
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

val krispyApplicationId = "io.github.crunchyinmilk.krispy"
val krispyVersion = getProperty("krispy.version")?.removePrefix("v") ?: project.getVersionName()
val krispyVersionCode = getProperty("krispy.version.code")?.let {
	requireNotNull(it.toIntOrNull()?.takeIf { code -> code > 0 }) { "krispy.version.code must be a positive Android versionCode" }
}
val krispyLocalSigning = Properties().apply {
	rootProject.file("release-signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun krispySigningProperty(name: String): String? =
	(getProperty(name) ?: krispyLocalSigning.getProperty(name))?.takeIf { it.isNotBlank() }

val krispyKeystoreFile = krispySigningProperty("krispy.keystore.file")
val krispyKeystorePassword = krispySigningProperty("krispy.keystore.password")
val krispySigningKeyAlias = krispySigningProperty("krispy.signing.key.alias")
val krispySigningKeyPassword = krispySigningProperty("krispy.signing.key.password")

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		applicationId = krispyApplicationId
		versionName = krispyVersion
		versionCode = krispyVersionCode ?: getVersionCode(versionName!!)
		buildConfigField("String", "KRISPY_UPDATE_OWNER", "\"crunchy-in-milk\"")
		buildConfigField("String", "KRISPY_UPDATE_REPOSITORY", "\"krispy\"")
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
		resValues = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	signingConfigs {
		if (krispyKeystoreFile != null && krispyKeystorePassword != null && krispySigningKeyAlias != null && krispySigningKeyPassword != null) {
			create("release") {
				storeFile = file(krispyKeystoreFile)
				storePassword = krispyKeystorePassword
				keyAlias = krispySigningKeyAlias
				keyPassword = krispySigningKeyPassword
			}
		}
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

			// Set package names used in various XML files
			resValue("string", "app_id", krispyApplicationId)
			resValue("string", "app_search_suggest_authority", "${krispyApplicationId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${krispyApplicationId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			buildConfigField("boolean", "DEVELOPMENT", "false")

			signingConfig = signingConfigs.findByName("release")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set package names used in various XML files
			val debugApplicationId = krispyApplicationId + applicationIdSuffix
			resValue("string", "app_id", debugApplicationId)
			resValue("string", "app_search_suggest_authority", "${debugApplicationId}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${debugApplicationId}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

base.archivesName.set("krispy-v$krispyVersion")

val validateKrispyReleaseVersion = tasks.register("validateKrispyReleaseVersion") {
	doLast {
		require(krispyVersionCode != null) { "Release builds require an explicit krispy.version.code greater than every published release" }
		require(getProperty("krispy.version") != null) { "Release builds require an explicit krispy.version" }
	}
}
val validateKrispyReleaseSigning = tasks.register("validateKrispyReleaseSigning") {
	doLast {
		require(android.signingConfigs.findByName("release") != null) {
			"Release signing is missing. Configure release-signing.properties locally or provide the KRISPY signing environment variables."
		}
		require(krispyKeystoreFile?.let { file(it).isFile } == true) { "The configured Krispy signing keystore does not exist." }
	}
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
	dependsOn(validateKrispyReleaseVersion)
	dependsOn(validateKrispyReleaseSigning)
}

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.design)
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.navigation3.ui)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.accompanist.permissions)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)
	implementation(libs.libass.media3)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
	testImplementation(libs.kotlinx.coroutines.test)
}
