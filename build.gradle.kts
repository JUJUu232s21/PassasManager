plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "semyon.android.studio.semyon_pass"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "semyon.android.studio.semyon_pass"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("net.zetetic:sqlcipher-android:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("androidx.sqlite:sqlite-framework:2.4.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
