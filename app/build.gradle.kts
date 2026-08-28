import java.util.Properties
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
}

val releaseSigningPropertiesFile = providers.gradleProperty("whfinSigningProperties")
    .map(::file)
    .getOrElse(file("${System.getProperty("user.home")}/.config/whfin/signing/release.properties"))
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningPropertiesPath = releaseSigningPropertiesFile.absolutePath

val verifyReleaseSigning by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails release builds unless the private WHFIN signing configuration is complete."
    commandLine(rootProject.file("scripts/verify-release-signing.sh"), releaseSigningPropertiesPath)
}

val verifyAndroidTestDevices by tasks.registering(Exec::class) {
    group = "verification"
    description = "Refuses connected Android tests when a physical device is online."
    commandLine(rootProject.file("scripts/assert-android-test-devices.sh"))
}

android {
    namespace = "dev.whekin.whfin"
    // Material 3 1.5 (expressive) and the Compose 1.12 runtime it brings compile against API 37.
    // targetSdk stays at 36: this buys the newer APIs, not the newer runtime behaviour.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.whekin.whfin"
        minSdk = 29
        targetSdk = 36
        versionCode = 26
        versionName = "0.3.14"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningPropertiesFile.isFile) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Debug/test installs must coexist with the data-bearing signed app. Even if a tool
            // accidentally targets a physical phone, a different package id prevents Gradle from
            // uninstalling the production sandbox to resolve a certificate mismatch.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        // Synthetic bank fixtures are used by JVM parser tests and by the on-device import harness.
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

tasks.matching {
    it.name.startsWith("connected") &&
        (it.name.endsWith("AndroidTest") || it.name == "connectedCheck")
}.configureEach {
    dependsOn(verifyAndroidTestDevices)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.configureEach {
    // One serialization runtime for the whole app. Compose 1.12 brings 1.7.3 through savedstate
    // while Room's migration testing brings 1.11.0, and AGP resolves test classpaths consistently
    // with the main one — so pinning the newer version test-only made the two irreconcilable.
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:${libs.versions.serialization.get()}",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:${libs.versions.serialization.get()}",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:${libs.versions.serialization.get()}",
        "org.jetbrains.kotlinx:kotlinx-serialization-bom:${libs.versions.serialization.get()}",
    )
}

dependencies {
    implementation(project(":core-ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.auth.api.phone)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.serialization.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
