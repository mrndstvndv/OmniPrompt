import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

val appVersion =
    project.findProperty("appVersion") as? String
        ?: error("appVersion is missing from gradle.properties")
val appVersionCode =
    project
        .findProperty("appVersionCode")
        ?.toString()
        ?.toIntOrNull()
        ?: error("appVersionCode is missing or invalid in gradle.properties")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.android.gms.oss-licenses-plugin")
}

abstract class SyncOssLicenseAssetsTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun syncAssets() {
        project.copy {
            from(sourceDir)
            include("third_party_license_metadata", "third_party_licenses")
            into(outputDir)
        }
    }
}

val syncReleaseOssLicensesForAssets by tasks.registering(SyncOssLicenseAssetsTask::class) {
    dependsOn("releaseOssLicensesTask")
    sourceDir.set(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask/raw"))
    outputDir.set(layout.buildDirectory.dir("generated/assets/ossLicenses"))
}

android {
    namespace = "com.mrndstvndv.search"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mrndstvndv.search"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props =
                    Properties().apply {
                        propsFile.inputStream().use { load(it) }
                    }
                storeFile = rootProject.file(props.getProperty("STORE_FILE") ?: "release.keystore")
                storePassword = props.getProperty("STORE_PASSWORD")
                keyAlias = props.getProperty("KEY_ALIAS")
                keyPassword = props.getProperty("KEY_PASSWORD")
            } else {
                storeFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "OmniPrompt Debug")
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncReleaseOssLicensesForAssets,
            SyncOssLicenseAssetsTask::outputDir,
        )
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
