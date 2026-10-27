plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildHudCollector by tasks.registering(Exec::class) {
    val nativeRoot = rootProject.projectDir.resolve("../native/lynxd")
    workingDir(nativeRoot)
    inputs.dir(nativeRoot.resolve("src"))
    inputs.file(nativeRoot.resolve("Cargo.toml"))
    val packaged = layout.buildDirectory.file("generated/hudJni/arm64-v8a/liblynxhud.so")
    outputs.file(packaged)
    commandLine("cargo", "build", "--release", "--target", "aarch64-linux-android")
    doLast {
        copy {
            from(nativeRoot.resolve("target/aarch64-linux-android/release/lynxd"))
            into(packaged.get().asFile.parentFile)
            rename { "liblynxhud.so" }
        }
        copy {
            from(nativeRoot.resolve("target/aarch64-linux-android/release/lynxd"))
            into(projectDir.resolve("src/main/assets/bin"))
            rename { "lynxd" }
        }
        copy {
            from(nativeRoot.resolve("target/aarch64-linux-android/release/lynxd"))
            into(projectDir.resolve("src/main/assets"))
            rename { "lynxd" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(buildHudCollector) }

android {
    namespace = "com.noir.lynx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noir.lynx"
        minSdk = 26
        targetSdk = 34
        versionCode = 20261309
        versionName = "3.2.73"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/hudJni"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/liblynxhud.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":freeform"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Coroutines for async state management
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore for local preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // TopJohnWu LibSU - Universal Root Bridge (Magisk, KernelSU, APatch)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")

    // JSON parsing (lightweight)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
