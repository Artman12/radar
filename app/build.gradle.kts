import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionPropsFile = rootProject.layout.projectDirectory.file("version.properties")
val versionPropsContent = providers.fileContents(versionPropsFile).asText.getOrElse("")
val versionProps = Properties().apply {
    if (versionPropsContent.isNotEmpty()) {
        load(versionPropsContent.reader())
    }
}
val appVersionMajor = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
val appVersionMinor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
val appVersionCode = versionProps.getProperty("VERSION_CODE", "1").toInt().coerceAtLeast(1)
val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionCode"

abstract class IncrementVersionTask : DefaultTask() {
    @get:Internal
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun increment() {
        val file = versionFile.get().asFile
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        val major = props.getProperty("VERSION_MAJOR", "1").toInt()
        val minor = props.getProperty("VERSION_MINOR", "0").toInt()
        val currentCode = props.getProperty("VERSION_CODE", "1").toInt().coerceAtLeast(1)
        val nextCode = currentCode + 1
        file.writeText(
            "VERSION_MAJOR=$major\nVERSION_MINOR=$minor\nVERSION_CODE=$nextCode\n"
        )
    }
}

val incrementReleaseVersionCode = tasks.register<IncrementVersionTask>("incrementReleaseVersionCode") {
    versionFile.set(versionPropsFile)
}

android {
    namespace = "com.onemanarmy.radar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.onemanarmy.radar"
        minSdk = 28
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "uk")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

base {
    archivesName.set("radar-$appVersionName")
}

tasks.matching {
    it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "signReleaseBundle")
}.configureEach {
    finalizedBy(incrementReleaseVersionCode)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.koin.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
