import com.clistery.gradle.AppDependencies

plugins {
    id("kre-publish")
    id("realm-android")
}

android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    
    defaultConfig {
        buildConfigField("long", "RECORD_DB_VERSION", "6")
        buildConfigField("String", "CALL_RECORD_DB", "\"call_record.realm\"")
        buildConfigField("long", "CALL_RECORD_RETRY_TIME", "5000")
        buildConfigField("long", "MAX_CALL_TIME_OFFSET", "60000")
        buildConfigField("long", "MIN_CALL_TIME_OFFSET", "100")
        buildConfigField("long", "START_CALL_TIME_OFFSET", "100")
    }
    lintOptions {
        isAbortOnError = false
    }
    buildTypes.configureEach {
        isMinifyEnabled = false
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
    AppDependencies.baseLibs.forEach { implementation(it) }
    implementation(AppDependencies.androidx.legacy)
    implementation(AppDependencies.clistery.appbasic)
    implementation(AppDependencies.clistery.appinject)
    implementation(AppDependencies.clistery.kotlin_realm_ext)
//    implementation(project(":library-base"))
    
    implementation("io.reactivex.rxjava2:rxandroid:2.1.0")
    //AndLinker
    implementation("com.codezjx.library:andlinker:0.7.1")
    
    getSDK19LayoutLibPath.forEach { compileOnly(files(it)) }
}
