import com.clistery.gradle.AppConfig
import com.clistery.gradle.AppDependencies
import com.clistery.gradle.implementation

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("realm-android")
    id("org.jetbrains.dokka")
    `maven-publish`
}

android {
    compileSdkVersion(AppConfig.compileSdk)
    buildToolsVersion(AppConfig.buildToolsVersion)
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    defaultConfig {
        minSdkVersion(AppConfig.minSdk)
        targetSdkVersion(AppConfig.targetSdk)
        versionCode(AppConfig.versionCode)
        versionName(AppConfig.versionName)
        
        buildConfigField("long", "RECORD_DB_VERSION", "4")
        buildConfigField("String", "CALL_RECORD_DB", "\"call_record.realm\"")
        buildConfigField("long", "CALL_RECORD_RETRY_TIME", "5000")
        buildConfigField("long", "MAX_CALL_TIME_OFFSET", "60000")
        buildConfigField("long", "MIN_CALL_TIME_OFFSET", "100")
    }
    lintOptions {
        isAbortOnError = false
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

val getSDK19LayoutLibPath = arrayOf(
    // "${android.sdkDirectory.absolutePath}/platforms/${android.compileSdkVersion}/data/layoutlib.jar"
    
    // "${android.sdkDirectory.absolutePath}/platforms/android-19/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-20/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-21/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-22/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-23/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-24/data/layoutlib.jar",
    "${android.sdkDirectory.absolutePath}/platforms/android-25/data/layoutlib.jar"
    // "${android.sdkDirectory.absolutePath}/platforms/android-26/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-27/data/layoutlib.jar",
    // "${android.sdkDirectory.absolutePath}/platforms/android-28/data/layoutlib.jar"
)

dependencies {
    implementation(AppDependencies.baseLibs)
    implementation(AppDependencies.androidx.legacy)
    compileOnly(AppDependencies.clistery.appinject)
    compileOnly(AppDependencies.clistery.kotlin_realm_ext)
    
    implementation("io.reactivex.rxjava2:rxandroid:2.1.0")
    //AndLinker
    implementation("com.codezjx.library:andlinker:0.7.1")
    
    getSDK19LayoutLibPath.forEach { compileOnly(files(it)) }
}
