import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream -> this.load(stream) }
    }
}
val youtubeApiKey = (localProps.getProperty("YOUTUBE_API_KEY")
    ?: (project.findProperty("YOUTUBE_API_KEY") as? String)
    ?: System.getenv("YOUTUBE_API_KEY")
    ?: "")
val youtubeCertSha1 = (localProps.getProperty("YOUTUBE_CERT_SHA1")
    ?: (project.findProperty("YOUTUBE_CERT_SHA1") as? String)
    ?: "0AB7DD30A89042C2CF6DADEF1EBA41775671B9AD")

android {
    namespace = "com.learnmate.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.learnmate.ai"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
        buildConfigField("String", "YOUTUBE_CERT_SHA1", "\"$youtubeCertSha1\"")
        buildConfigField("String", "YOUTUBE_PACKAGE_NAME", "\"com.learnmate.ai\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = (project.findProperty("RELEASE_STORE_FILE") as? String) ?: "../keystore/release.keystore"
            val storePasswordProp = (project.findProperty("RELEASE_STORE_PASSWORD") as? String) ?: "LearnMateRelease2026!"
            val keyAliasProp = (project.findProperty("RELEASE_KEY_ALIAS") as? String) ?: "learnmate-release"
            val keyPasswordProp = (project.findProperty("RELEASE_KEY_PASSWORD") as? String) ?: "LearnMateRelease2026!"

            storeFile = file(storeFilePath)
            storePassword = storePasswordProp
            keyAlias = keyAliasProp
            keyPassword = keyPasswordProp
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            resValue("string", "app_name", "LearnMate Staging")
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Android Credential Manager & Google ID (Gate 6.16 P1.2)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Room Persistence & Serialization
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.google.gson)

    // On-Device ExecuTorch Android 1.4.0 Runtime (Gate 2 Inference Spike)
    implementation(libs.pytorch.executorch.android)
    implementation(libs.facebook.fbjni)

    // Cryptography Fallback for Ed25519 on Android API 28-32
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    androidTestImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    debugImplementation(libs.androidx.ui.tooling)
}

