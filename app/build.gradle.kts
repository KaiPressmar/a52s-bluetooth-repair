plugins { id("com.android.application") }

val releaseVersion = providers.environmentVariable("RELEASE_VERSION").orElse("0.11.0")
val releaseVersionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orElse("12")
val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE")
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = signingStoreFile.isPresent && signingStorePassword.isPresent && signingKeyAlias.isPresent && signingKeyPassword.isPresent

android {
    namespace = "de.kaipressmar.a52srepair"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.kaipressmar.a52srepair"
        minSdk = 31
        targetSdk = 36
        versionCode = releaseVersionCode.get().toInt()
        versionName = releaseVersion.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "device"
    productFlavors {
        create("a52s") {
            dimension = "device"
            // Samsung's final official A52s runtime is Android 14 / One UI 6.1.
            // Compile with API 36 for current toolchain support, but target the device's real OS.
            targetSdk = 34
        }
        create("s22") {
            dimension = "device"
            targetSdk = 36
        }
    }

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
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
