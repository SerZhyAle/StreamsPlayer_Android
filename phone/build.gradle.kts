import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("streamsplayer.android-application")
    id("streamsplayer.distribution-variants")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sza.streamsplayer"

    defaultConfig {
        // Frozen anchor (owner, 2026-10-02): one id for the phone and the watch app, because the Data Layer
        // routes by (package name, signing certificate) and drops a message whose package name differs
        // across the two devices. The noLegal variant shares it too. Never a suffix, never a rename once a
        // package ships.
        applicationId = "com.sza.streamsplayer"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("debug").assets.srcDirs(files("$projectDir/schemas"))
        getByName("test").assets.srcDirs(files("$projectDir/schemas"))
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(":exchange-core:test")
    // The full catalog/Room and shell suite outgrows Gradle's default test heap.
    maxHeapSize = "1536m"
    maxParallelForks = 1
    forkEvery = 75
}

dependencies {
    implementation(project(":exchange-core"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.okhttp.okhttp)
    implementation(libs.gson)
    implementation(libs.timber)
    implementation(libs.google.gms.play.services.wearable)
    implementation(libs.google.gms.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.rtsp.server) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    implementation(libs.rootencoder.library) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    implementation(libs.rootencoder.common) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
