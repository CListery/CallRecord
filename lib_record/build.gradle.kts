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
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    
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

val androidJavadocs by tasks.register<Javadoc>("androidJavadocs") {
    options {
        encoding = Charsets.UTF_8.displayName()
        source = android.sourceSets.flatMap { it.java.srcDirs }.first().absolutePath
        classpath =
            classpath.plus(project.files(android.bootClasspath.joinToString(File.pathSeparator)))
        exclude(listOf("**/*.kt", "**/BuildConfig.java", "**/R.java"))
        isFailOnError = true
        if (this is StandardJavadocDocletOptions) {
            links("http://docs.oracle.com/javase/8/docs/api/")
            linksOffline("http://d.android.com/reference", "${android.sdkDirectory}/docs/reference")
        }
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask> {
    // dependsOn(androidJavadocs)
    dokkaSourceSets {
        named("main") {
            noStdlibLink.set(true)
            noAndroidSdkLink.set(true)
            noJdkLink.set(true)
            includeNonPublic.set(true)
            skipEmptyPackages.set(true)
        }
    }
    offlineMode.set(true)
    // outputDirectory.set(androidJavadocs.destinationDir)
}
val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}
val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-doc")
}
val androidSourcesJar by tasks.register<Jar>("androidSourcesJar") {
    from(android.sourceSets.flatMap { it.java.srcDirs })
    archiveClassifier.set("sources")
}

publishing {
    repositories {
        maven {
            name = "_ProjectMaven_"
            url = uri(extra.get("PROJECT_LOCAL_MAVEN_PATH")?.toString()!!)
        }
        maven {
            name = "_jfrog.fx_"
            url = uri(extra.get("MAVEN_REPOSITORY_URL")?.toString()!!)
            credentials {
                username = extra.get("artifactory_maven_user")?.toString()!!
                password = extra.get("artifactory_maven_pwd")?.toString()!!
            }
        }
    }
    publications {
        create<MavenPublication>("-Release") {
            groupId = AppConfig.GROUP_ID
            artifactId = AppConfig.ARTIFACT_ID
            version = AppConfig.versionName
            
            suppressAllPomMetadataWarnings()
            pom {
                name.set(rootProject.name)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("cyh")
                        name.set("CListery")
                        email.set("cai1083088795@gmail.com")
                    }
                }
            }
            
            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)
            artifact(androidSourcesJar)
            afterEvaluate { artifact(tasks.getByName("bundleReleaseAar")) }
        }
    }
}
