import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.littlehelper"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.littlehelper"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "DEEPSEEK_API_KEY",
            "\"${localProperties.getProperty("DEEPSEEK_API_KEY", "")}\""
        )

        // 火山引擎 ASR — 填入 local.properties 中的真实值
        buildConfigField(
            "String",
            "VOLC_APPID",
            "\"${localProperties.getProperty("VOLC_APPID", "")}\""
        )
        buildConfigField(
            "String",
            "VOLC_TOKEN",
            "\"${localProperties.getProperty("VOLC_TOKEN", "")}\""
        )
        buildConfigField(
            "String",
            "VOLC_CLUSTER",
            "\"${localProperties.getProperty("VOLC_CLUSTER", "")}\""
        )
        buildConfigField(
            "String",
            "VOLC_STREAMING_CLUSTER",
            "\"${localProperties.getProperty("VOLC_STREAMING_CLUSTER", "")}\""
        )
        buildConfigField(
            "String",
            "VOLC_RESOURCE_ID",
            "\"${localProperties.getProperty("VOLC_RESOURCE_ID", "")}\""
        )
        buildConfigField(
            "String",
            "VOLC_STREAMING_WS_URL",
            "\"${localProperties.getProperty("VOLC_STREAMING_WS_URL", "")}\""
        )

        buildConfigField(
            "boolean",
            "USE_OPENCLAW_SHELL",
            localProperties.getProperty("USE_OPENCLAW_SHELL", "false")
        )

        buildConfigField(
            "boolean",
            "USE_OPENCLAW_MOCK",
            localProperties.getProperty("USE_OPENCLAW_MOCK", "true")
        )

        buildConfigField(
            "String",
            "OPENCLAW_GATEWAY_HOST",
            "\"${localProperties.getProperty("OPENCLAW_GATEWAY_HOST", "192.168.1.55")}\""
        )
        buildConfigField(
            "int",
            "OPENCLAW_GATEWAY_PORT",
            localProperties.getProperty("OPENCLAW_GATEWAY_PORT", "18789")
        )
        buildConfigField(
            "String",
            "OPENCLAW_GATEWAY_PASSWORD",
            "\"${localProperties.getProperty("OPENCLAW_GATEWAY_PASSWORD", "clawbot-test-2024")}\""
        )
        buildConfigField(
            "String",
            "OPENCLAW_GATEWAY_TOKEN",
            "\"${localProperties.getProperty("OPENCLAW_GATEWAY_TOKEN", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
