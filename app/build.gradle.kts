import com.clistery.gradle.AppConfig
import com.clistery.gradle.AppDependencies
import com.clistery.gradle.implementation

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("realm-android")
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
        applicationId = "com.yh.callrecord"
        minSdkVersion(AppConfig.minSdk)
        targetSdkVersion(AppConfig.targetSdk)
        versionCode(AppConfig.versionCode)
        versionName(AppConfig.versionName)
    
        testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
        multiDexEnabled = true
        
        buildConfigField("String", "CALL_RECORD_DB", "\"CallRecord\"")
        buildConfigField("long", "RECORD_DB_VERSION", "1")
        buildConfigField("int", "MAX_RETRY_SYNC_RECORD_COUNT", "5")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // debuggable true
        }
    }
}

dependencies {
    implementation(AppDependencies.baseLibs)
    implementation(AppDependencies.androidx.legacy)
    
    implementation(AppDependencies.clistery.appinject)
    implementation(AppDependencies.clistery.kotlin_realm_ext)
    //AndLinker
    implementation("com.codezjx.library:andlinker:0.7.1")
    implementation(project(mapOf("path" to ":lib_record")))
    //    implementation "com.clistery.app:callrecord:1.3.10"
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.multidex:multidex:2.0.1")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test:rules:1.0.2")
    androidTestImplementation("com.google.truth:truth:0.42")
}
