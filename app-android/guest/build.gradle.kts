import java.util.Properties

// DualOS inside the guest (LineageOS 23, Project Infinity-X 3.5): "Back to One UI"
// in one tap, from its screen or its Quick Settings tile. It only writes a request
// file in its own data folder (files/oneos_return); the session's watcher on the host
// (switcher/assets/oneos_switch.sh) sees it and returns. guest_prepare.sh installs it
// in every guest as a system app (/oem/app/OneOSGuest), from $STAGE/OneOSGuest.apk.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release key, as :app signs with it (never in the repo).
val releaseKeys: Properties? =
    File(System.getProperty("user.home"), ".oneos-release/keystore.properties").takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use(::load) }
    }

android {
    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("shared-guest-ui"))
    namespace = "com.fahadali.oneos.guest"
    compileSdk { version = release(37) }

    defaultConfig {
        // Kept as com.fahadali.oneos.guest because host/guest scripts & guest_root broker
        // authenticate and poll /data/user/0/com.fahadali.oneos.guest/files/.
        applicationId = "com.fahadali.oneos.guest"
        minSdk = 31
        targetSdk = 36
        versionCode = 20
        versionName = "2.0"
    }

    signingConfigs {
        if (releaseKeys != null) {
            create("release") {
                storeFile = file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

// A release without the key would be an unsigned APK: refuse instead, as :app does.
tasks.matching { it.name == "packageRelease" }.configureEach {
    val hasKey = releaseKeys != null
    doFirst {
        check(hasKey) { "No release key: ~/.oneos-release/keystore.properties is missing (see app-android/README.md, section Build)." }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.google.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
}
