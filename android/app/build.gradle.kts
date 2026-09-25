plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val offiaBuildCommit = providers.environmentVariable("GITHUB_SHA").orElse("desconhecida").get()

android {
    namespace = "ia.off"
    compileSdk = 36
    ndkVersion = "29.0.13113456"

    signingConfigs {
        create("alpha") {
            // Public TEST-ONLY key for install-over-install alpha builds.
            // Production releases must use a private key from CI secrets.
            storeFile = rootProject.file("offia-alpha.jks")
            storePassword = "offia-alpha"
            keyAlias = "offia-alpha"
            keyPassword = "offia-alpha"
        }
    }

    defaultConfig {
        applicationId = "ia.off"
        minSdk = 33
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.0-alpha.8-v2-context-test"
        buildConfigField("String", "OFFIA_COMMIT", "\"$offiaBuildCommit\"")
        buildConfigField("String", "MEMORIA_IA_VERSION", "\"v2-post-rc2-context-fix\"")
        buildConfigField("String", "MEMORIA_IA_COMMIT", "\"89a9226ac7bf52afff190531bf956b144e64ce82\"")
        buildConfigField("String", "BDR_VERSION", "\"v1.2.0-rc4\"")
        buildConfigField("String", "BDR_COMMIT", "\"317882a00f041fc1568ff986af8016b09453f21a\"")
        buildConfigField("String", "LLAMA_CPP_COMMIT", "\"ca3d5a3e10d53f7ea672cb9b6178faca3e2807bc\"")
        buildConfigField("String", "KV_CACHE_TYPE", "\"Q8_0\"")
        buildConfigField("String", "MEMORIA_MOBILE_ABI", "\"v1\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("alpha")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    androidTestImplementation(bom)

    implementation(project(":llama-lib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
