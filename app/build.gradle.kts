plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.kwonsu.standby"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "dev.kwonsu.standby"
        minSdk = 31
        targetSdk = 36
        versionCode = 26
        versionName = "0.13.0"
        manifestPlaceholders["appLabel"] = "Fold 스탠바이"
        manifestPlaceholders["notificationListenerLabel"] = "Fold 스탠바이 알림 패널"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        create("validation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".validation"
            versionNameSuffix = "-validation"
            matchingFallbacks += listOf("debug")
            manifestPlaceholders["appLabel"] = "Fold 스탠바이 검사"
            manifestPlaceholders["notificationListenerLabel"] = "Fold 스탠바이 검사 알림 패널"
        }
    }
    testBuildType = "validation"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    lint { warningsAsErrors = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.window:window:1.4.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    add("validationImplementation", "androidx.compose.ui:ui-test-manifest")
}
