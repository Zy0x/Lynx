plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.noir.lynx.freeform"
    resourcePrefix = "lynx_freeform_"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.topjohnwu.libsu:service:5.2.2")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    systemProperty("freeform.resources", file("src/main/res").absolutePath)
}
