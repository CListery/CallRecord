import com.clistery.gradle.AppDependencies

plugins {
    id("app")
    id("realm-android")
}

android {
    defaultConfig {
        buildConfigField("String", "CALL_RECORD_DB", "\"CallRecord\"")
        buildConfigField("long", "RECORD_DB_VERSION", "1")
        buildConfigField("int", "MAX_RETRY_SYNC_RECORD_COUNT", "5")
    }
}

dependencies {
    AppDependencies.baseLibs.forEach { implementation(it) }
    implementation(AppDependencies.androidx.legacy)
    
    implementation(AppDependencies.clistery.appbasic)
    implementation(AppDependencies.clistery.appinject)
    implementation(AppDependencies.clistery.kotlin_realm_ext)
    implementation(AppDependencies.clistery.streamARL)
    implementation("com.clistery.app:jsonholder:1.1.0")
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
