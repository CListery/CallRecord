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
}
