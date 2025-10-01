import java.util.Properties

val ksProps = Properties()
val ksFile = rootProject.file("keystore.properties")
val hasKeystore = ksFile.exists()
if (hasKeystore) ksFile.inputStream().use { ksProps.load(it) }

android {
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
        getByName("release") {
            isMinifyEnabled = false
            // keystore 있을 때만 서명 사용
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            // 디버그는 기본 debug keystore 사용
        }
    }
}
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // ✅ KSP 사용 (KAPT 쓰지 않음)
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

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

    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

/** 🔧 annotation-experimental 충돌 방지 (있으면 유효) */
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

    // ✅ Room (KSP 사용)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // (선택) 명시적으로 하나만 쓰기
    implementation("androidx.annotation:annotation-experimental:1.4.0")
}

kotlin { jvmToolchain(17) }
