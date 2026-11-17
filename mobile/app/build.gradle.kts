import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

// Debug builds talk to a backend on the development machine; the emulator reaches it at 10.0.2.2.
// Override with: ./gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8081/
val debugApiBaseUrl = providers.gradleProperty("apiBaseUrl").getOrElse("http://10.0.2.2:8080/")

android {
    namespace = "com.closecircle.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.closecircle.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://closecircle-api.onrender.com/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.maplibre)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

// Coverage gate for the code that holds the logic (network, repository, domain rules, view models).
// The Compose screens are checked on a device, not by unit tests, so they are not part of this number.
val coveredClasses = listOf(
    "com/closecircle/app/data/**",
    "com/closecircle/app/domain/**",
    "com/closecircle/app/ui/*ViewModel*",
)
val classDirs = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { include(coveredClasses) }
val execFile = layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")

tasks.register<JacocoReport>("coverageReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(classDirs)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(execFile)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("coverageVerification") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(classDirs)
    executionData.setFrom(execFile)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
