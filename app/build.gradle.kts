import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.travelneveralone.tna"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.travelneveralone.android"
        minSdk = 26
        targetSdk = 35
        // Build number like iOS: YYMMDD.HHMM, set by scripts/build.sh. It is what
        // testers read at the bottom of Profile and type into bug reports, so
        // every build must differ; a build from Android Studio shows "dev".
        val tnaBuild = (findProperty("tnaBuild") as String?)?.takeIf { it.isNotBlank() } ?: "dev"
        versionCode = (findProperty("tnaCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"
        buildConfigField("String", "TNA_BUILD", "\"$tnaBuild\"")
    }

    flavorDimensions += "env"
    productFlavors {
        create("beta") {
            dimension = "env"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "SUPABASE_URL", "\"https://jpodfdkhxjikomqibxsc.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_vRMY5V40B74q2I-rrPd0Zg_q2ZM1jH1\"")
            buildConfigField("boolean", "IS_PROD", "false")
            resValue("string", "app_name", "TNA β")
        }
        create("prod") {
            dimension = "env"
            // Self-hosted Supabase on our own server in Russia (same as iOS AppConfig).
            buildConfigField("String", "SUPABASE_URL", "\"https://api.travelneveralone.com\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_-VbODF6P1E_LIbF8di-wUS_FxOduIzN\"")
            buildConfigField("boolean", "IS_PROD", "true")
            resValue("string", "app_name", "TNA")
        }
    }

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines)
    implementation(libs.coil)
    implementation(libs.androidx.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    // Maps for chat locations: OpenStreetMap, no Google Play services or API key.
    implementation(libs.osmdroid)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    testImplementation(libs.junit)
}
