import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.appblocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.appblocker"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "2.2"

        // Inject SMTP Credentials from local.properties
        val properties = Properties()
        val file = project.rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { properties.load(it) }
        }
        
        val smtpUser = properties.getProperty("SMTP_USER") ?: ""
        buildConfigField("String", "SMTP_USER", "\"$smtpUser\"")

        val smtpPass = properties.getProperty("SMTP_PASS") ?: ""
        buildConfigField("String", "SMTP_PASS", "\"$smtpPass\"")
        val geminiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Fix: JavaMail libs ship duplicate META-INF files — exclude them
    packaging {
        resources {
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // JavaMail for SMTP email sending
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // WorkManager for weekly background scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Google Gemini API (Flash-Lite for Logic Puzzles)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
