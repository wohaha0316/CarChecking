import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val ksProps = Properties()
val ksFile = rootProject.file("keystore.properties")
val hasKeystore = ksFile.exists()
if (hasKeystore) ksFile.inputStream().use { ksProps.load(it) }

android {
    namespace = "com.example.carchecking"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carchecking"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(ksProps["storeFile"] ?: "")
                storePassword = ksProps["storePassword"] as String?
                keyAlias = ksProps["keyAlias"] as String?
                keyPassword = ksProps["keyPassword"] as String?
            }
        }
    }

    buildTypes {
        debug {
            // 디버그는 기본 debug keystore 사용
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore 있을 때만 서명 사용
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

/** 🔧 annotation-experimental 충돌 방지 */
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.annotation" && requested.name == "annotation-experimental") {
            useVersion("1.4.0")
        }
    }
}

/** ✅ KSP용 Room 옵션 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0") {
        exclude(group = "androidx.annotation", module = "annotation-experimental")
    }
    implementation("com.google.android.material:material:1.12.0") {
        exclude(group = "androidx.annotation", module = "annotation-experimental")
    }
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Apache POI
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")

    // ✅ Room (KSP)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 명시적으로 하나만 유지
    implementation("androidx.annotation:annotation-experimental:1.4.0")

    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // ML Kit - Text Recognition (on-device)
    implementation("com.google.mlkit:text-recognition:16.0.0")
}

kotlin {
    jvmToolchain(17)
}