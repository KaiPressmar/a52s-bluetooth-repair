plugins {
    alias(libs.plugins.android.application)
}

val releaseVersion = providers.environmentVariable("RELEASE_VERSION").orElse("0.16.0")
val releaseVersionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orElse("17")
val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE")
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
val hasReleaseSigning =
    signingStoreFile.isPresent && signingStorePassword.isPresent &&
        signingKeyAlias.isPresent && signingKeyPassword.isPresent

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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
