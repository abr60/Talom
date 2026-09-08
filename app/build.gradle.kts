plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.talom"
    compileSdk = 35
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.talom"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = file("talom-release.keystore")
            if (storeFilePath.exists()) {
                storeFile = storeFilePath
                storePassword = (project.findProperty("TALOM_RELEASE_STORE_PASSWORD") as String?)
                    ?: System.getenv("TALOM_RELEASE_STORE_PASSWORD")
                keyAlias = (project.findProperty("TALOM_RELEASE_KEY_ALIAS") as String?)
                    ?: System.getenv("TALOM_RELEASE_KEY_ALIAS") ?: "talom"
                keyPassword = (project.findProperty("TALOM_RELEASE_KEY_PASSWORD") as String?)
                    ?: System.getenv("TALOM_RELEASE_KEY_PASSWORD")
                        ?: storePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null && releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
