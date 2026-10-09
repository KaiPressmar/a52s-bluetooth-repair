plugins {
    alias(libs.plugins.android.application)
}

val releaseVersion = providers.environmentVariable("RELEASE_VERSION").orElse("0.18.0")
val releaseVersionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orElse("20")
val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE")
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
val hasReleaseSigning =
    signingStoreFile.isPresent && signingStorePassword.isPresent &&
        signingKeyAlias.isPresent && signingKeyPassword.isPresent

android {
    namespace = "de.kaipressmar.a52srepair"
    // Compile against the newest SDK (required by current AndroidX); targetSdk stays at 36 so
    // Android 17 behavior changes are adopted deliberately, not as a side effect.
    compileSdk = 37

    defaultConfig {
        applicationId = "de.kaipressmar.a52srepair"
        minSdk = 31
        targetSdk = 36
        versionCode = releaseVersionCode.get().toInt()
        versionName = releaseVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One universal APK for Android 12-16. Device- and Android-version-specific behavior is
    // decided at runtime (DeviceFamily, Build.VERSION.SDK_INT), so no wrong build can be installed.

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile.get())
                storePassword = signingStorePassword.get()
                keyAlias = signingKeyAlias.get()
                keyPassword = signingKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // English default + German; generates android:localeConfig for the per-app language
        // setting (Android 13+). The default locale is declared in res/resources.properties.
        generateLocaleConfig = true
        // Ship only the app's languages, so library UI (dialogs, pickers) never mixes in a third one.
        localeFilters += setOf("en", "de")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric 4.17's Android 16 runtime reads FileDescriptor internals through
        // jdk.internal.access, which JDK 17+ does not export to unnamed modules.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
