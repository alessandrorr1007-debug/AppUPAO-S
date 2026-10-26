plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.upaos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.upaos"
        minSdk = 24
        targetSdk = 37
        versionCode = 25
        versionName = "1.7.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("distribution") {
            val signingPath = System.getenv("UPAOS_KEYSTORE_PATH")
            if (!signingPath.isNullOrBlank()) {
                storeFile = file(signingPath)
                storePassword = System.getenv("UPAOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPAOS_KEY_ALIAS")
                keyPassword = System.getenv("UPAOS_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            if (!System.getenv("UPAOS_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("distribution")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Opt-in instrumentation against the actual optimized release, using a separately supplied test key.
    testBuildType = providers.gradleProperty("upaosTestBuildType").getOrElse("debug").also {
        require(it in setOf("debug", "release")) { "upaosTestBuildType must be debug or release" }
    }
    sourceSets {
        getByName("test").java.srcDir("src/contractTest/java")
        getByName("androidTest").java.srcDir("src/contractTest/java")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    
    // Jetpack Compose & Navigation
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Retrofit, OkHttp, Gson & Security
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.google.code.gson:gson:2.11.0")
    // Read old encrypted preferences only, for upgrades from versions before SecureStore.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")

    // Glance (Widgets modernos con Compose)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // WorkManager para tareas en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
