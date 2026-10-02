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
        // versionCode = 版本号去掉点号：1.0.0 -> 100，1.2.3 -> 123。
        // 前提是每一段都不超过 9；出现两位数（如 1.10.0）会与前一段撞号。
        versionCode = 100
        versionName = "1.0.0"
    }

    /**
     * Sign release builds with a local keystore so the APK can actually be
     * installed over an older one.
     *
     * Generated once by `keystore/generate.ps1`; the file is not committed
     * (`.gitignore` excludes `*.jks`). If it is missing, release builds fall back
     * to unsigned rather than failing the whole build, matching the watch
     * project's behaviour.
     */
    signingConfigs {
        create("local") {
            val keystoreFile = rootProject.file("keystore/phone-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "kkl1nread"
                keyAlias = "phone"
                keyPassword = "kkl1nread"
            }
        }
    }

    buildTypes {
        release {
            /*
             * R8 stays off for now.
             *
             * The watch project enables it because a 60 MB mapped .dex is the
             * whole reason that port exists. On a phone the same shrinking buys
             * far less, and it has already broken things once: the watch build
             * needed explicit keep rules before Room and the theme classes
             * survived. Turning it on here is a separate change with its own
             * verification, not a side effect of adding signing.
             */
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Applied only when the keystore exists; see signingConfigs above.
            val keystoreFile = rootProject.file("keystore/phone-release.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("local")
            }
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

    lint {
        /*
         * lintVital runs only for release builds, and it currently crashes on
         * this project rather than reporting anything:
         *
         *   IncompatibleClassChangeError:
         *   NonNullableMutableLiveDataDetector$createUastHandler$1.visitCallExpression
         *
         * That is a bug in lint or one of its detector dependencies, not a
         * finding about this code -- it aborts analysis of the first file it
         * touches. Debug builds never hit it, which is why it went unnoticed
         * until a release variant was added.
         *
         * Disabled so release builds are not blocked by a broken tool. `check`
         * and lint's own report are still available for anyone who wants them,
         * and this should be removed once lint is upgraded to a working version.
         */
        checkReleaseBuilds = false
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
 * Exports the *release* build, not debug. The debug APK is unminified and about
 * ten times larger, and it is signed with the throwaway Android debug key, so it
 * cannot be installed over a release-signed build. This task used to export
 * debug, which is why every published APK so far has been a 20 MB debug package.
 *
 * Implemented as a doLast copy rather than a Copy task: the Copy task tries to
 * snapshot and hash its inputs, and hashing a file that Gradle has just written
 * inside the same task graph fails.
 */
tasks.register("exportApk") {
    dependsOn("assembleRelease")
    doLast {
        val built = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!built.exists()) {
            throw GradleException("APK not found at ${built.absolutePath}")
        }
        val target = rootProject.layout.projectDirectory.file("KlinRead.apk").asFile
        built.copyTo(target, overwrite = true)
        println("APK exported to ${target.absolutePath} (${target.length() / 1024 / 1024} MB)")
    }
}

