plugins {
    id("com.android.application")
}

android {
    namespace = "app.dsh.widget"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.dsh.widget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.9.1")
}
