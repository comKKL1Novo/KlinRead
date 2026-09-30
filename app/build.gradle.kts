plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.klin.read"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.klin.read"
        minSdk = 24
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Settings shows the real version via BuildConfig.VERSION_NAME, so the
        // number can never drift from what the APK was built with.
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            // Never restart the test worker between classes.
            //
            // Note this does NOT move tests into the Gradle process: the test
            // task always runs through a forked worker. In some restricted shells
            // that worker cannot be launched at all and the task fails with
            // `ClassNotFoundException: GradleWorkerMain`, which this setting does
            // not fix. Use tools/run-tests.ps1 to bypass Gradle in that case.
            //
            // These are pure JVM logic tests, so a fresh worker per class buys
            // nothing anyway.
            it.setForkEvery(0)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Avatar image loading.
    implementation(libs.coil.compose)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation(libs.junit)
}

/**
 * Copies the built APK into the project root as KlinRead.apk.
 *
 * The default output path is buried several folders deep, which makes the
 * installable file awkward to find. Dropping a copy next to the project keeps a
 * usable build one click away after every assemble.
 *
 * Implemented as a doLast copy rather than a Copy task: the Copy task tries to
 * snapshot and hash its inputs, and hashing a file that Gradle has just written
 * inside the same task graph fails.
 */
tasks.register("exportApk") {
    dependsOn("assembleDebug")
    doLast {
        val built = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (!built.exists()) {
            throw GradleException("APK not found at ${built.absolutePath}")
        }
        val target = rootProject.layout.projectDirectory.file("KlinRead.apk").asFile
        built.copyTo(target, overwrite = true)
        println("APK exported to ${target.absolutePath} (${target.length() / 1024 / 1024} MB)")
    }
}

