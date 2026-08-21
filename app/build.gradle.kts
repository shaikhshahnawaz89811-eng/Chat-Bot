plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // Phase 3: Room's annotation processor (api_keys table)
}

android {
    namespace = "com.brain.offlineai"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.brain.offlineai"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true

        // Real device target: arm64-v8a covers virtually every phone this
        // app will actually run on. x86_64 is left out on purpose (adds a
        // lot of CI build time for emulator-only coverage); add it back
        // to this list if you need to run the local engine on an x86_64
        // emulator/Chromebook.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Phase 3: API Keys module storage. Room is the DB layer; SQLCipher
    // (net.zetetic) supplies a real SupportSQLiteOpenHelper.Factory so the
    // whole Room database file is AES-256 encrypted at rest, not just
    // access-controlled - a real API secret sitting in a plain unencrypted
    // SQLite file on a rooted/backed-up device would defeat the point of
    // "Secure API Key System" from the mockup's own highlight badge.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")
    // Holds the random SQLCipher passphrase itself behind Android Keystore
    // (AES256-GCM), so the encryption key isn't just sitting in plain
    // SharedPreferences next to the DB it protects.
    // Phase 22 (real, user-supplied Tavily web-search provider) reuses
    // this same security-crypto dependency for WebSearchKeyStore's
    // EncryptedSharedPreferences, and org.json (already pulled in by
    // Phase 4 below) for parsing Tavily's real JSON response - no new
    // Gradle dependency needed for outbound HTTPS either, since
    // HttpURLConnection ships with the JDK/Android itself (Rule 20).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Phase 4: Local API Server. NanoHTTPD is a real, tiny embeddable HTTP
    // server - the actual bind policy is enforced in LocalApiServer itself.
    // Request/response JSON uses
    // org.json, which already ships with Android - no extra JSON dep
    // needed (Rule 20 - only what this phase actually needs).
    // NotificationCompat (foreground-service notification) comes from
    // androidx.core, already pulled in transitively by core-ktx above -
    // no extra line needed for it.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Phase 24 - real PDF text extraction (PROGRESS.md's own recorded open
    // gap: "PDF/Word/any document content is never actually read"). This
    // is a real, offline, on-device Android port of Apache PDFBox - no
    // network call at runtime, same offline-only posture every other real
    // capability in this app already holds itself to. Only PDF is wired
    // this phase (see AttachmentContentReader.readPdfTextPreview) - Word
    // (.doc/.docx) reading is still a real, separate, not-yet-started gap
    // (different real format, would need a different real library
    // decision - not silently bundled in under the same claim).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Compute Bridge QR camera scan: real on-device camera preview
    // (CameraX) feeding a real on-device barcode decoder (ML Kit Barcode
    // Scanning bundled model - com.google.mlkit:barcode-scanning is a
    // fully offline, on-device model; it does not call any Google network
    // API at runtime, so this does not violate the app's "100% Offline"
    // posture the same way the paste-only pairing-code field never did.
    // This replaces the old "scan with any camera app, then paste"
    // workaround in ComputeBridgeScreen with an in-app scanner that feeds
    // ComputeBridgeViewModel.pairFromCode() directly.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
