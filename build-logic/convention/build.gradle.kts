plugins {
    `kotlin-dsl`
}

group = "com.sza.streamsplayer.buildlogic"

dependencies {
    // compileOnly: the root build puts the Android Gradle Plugin on the classpath once (build.gradle.kts
    // of the repository root, `apply false`), and the plugins below find it there at run time.
    compileOnly(libs.android.gradle.plugin)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "streamsplayer.android-application"
            implementationClass = "com.sza.streamsplayer.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("distributionVariants") {
            id = "streamsplayer.distribution-variants"
            implementationClass = "com.sza.streamsplayer.buildlogic.DistributionVariantsConventionPlugin"
        }
    }
}
