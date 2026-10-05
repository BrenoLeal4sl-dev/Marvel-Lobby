import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    val localProperties = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val marvelApiKey = localProperties.getProperty("MARVEL_API_KEY", "")
        .replace("\\", "\\\\").replace("\"", "\\\"")
    fun configured(name: String, fallback: String = "") = localProperties.getProperty(name, fallback)
        .replace("\\", "\\\\").replace("\"", "\\\"")
    buildFeatures { buildConfig = true }
    namespace = "com.example.marvellobby"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.marvellobby"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MARVEL_API_KEY", "\"$marvelApiKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${configured("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${configured("GEMINI_MODEL", "gemini-2.5-flash")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${configured("GROQ_API_KEY")}\"")
        buildConfigField("String", "GROQ_TRANSLATION_MODEL", "\"${configured("GROQ_TRANSLATION_MODEL", "openai/gpt-oss-20b")}\"")
        buildConfigField("String", "LOBBY_API_BASE_URL", "\"${configured("LOBBY_API_BASE_URL")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
