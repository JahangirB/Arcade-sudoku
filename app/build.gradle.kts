plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI stamps each build with its run number, so every published APK has a higher versionCode
// than the one before. Local builds are version 1 and never count as newer than a release.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// The id Android and Firebase know the app by. The Kotlin code keeps its original
// com.arcade.sudoku package (the namespace below); only this id changed with the rename.
val appId = "com.gamesuite.app"

// Push invitations use Firebase, which is optional: put google-services.json from the Firebase
// console into app/ to switch them on. It is read by hand (no Google Services plugin), so the
// app still builds without it and invitations then stay off.
val firebase: Map<String, String> = file("google-services.json").takeIf { it.exists() }?.let { f ->
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parse(f) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val client = (json["client"] as List<Map<String, Any?>>).first { c ->
        val info = c["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == appId
    }
    @Suppress("UNCHECKED_CAST")
    mapOf(
        "PROJECT_ID" to project["project_id"].toString(),
        "SENDER_ID" to project["project_number"].toString(),
        "APP_ID" to (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"].toString(),
        "API_KEY" to (client["api_key"] as List<Map<String, Any?>>).first()["current_key"].toString(),
    )
} ?: emptyMap()

android {
    namespace = "com.arcade.sudoku"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        for (key in listOf("PROJECT_ID", "SENDER_ID", "APP_ID", "API_KEY")) {
            buildConfigField("String", "FIREBASE_$key", "\"${firebase[key].orEmpty()}\"")
        }

        // Where the in-app updater looks for new releases (the README download button's release).
        buildConfigField(
            "String", "UPDATE_BASE_URL",
            "\"https://github.com/JahangirB/Game-Suite/releases/latest/download\"",
        )
    }

    signingConfigs {
        // A fixed, publicly known debug key (not for the Play Store). Every CI build is signed
        // with it, so a newly downloaded APK installs over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.nearby)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
