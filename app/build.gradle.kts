import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing credentials live in keystore/keystore.properties (gitignored).
// Missing file -> release builds fall back to debug signing so the project stays buildable for anyone.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "io.github.coderirse.reps"
    compileSdk {
        // Compile-only bump from 36.1: latest stable androidx artifacts
        // (navigation 2.10.x, lifecycle 2.11.x compose) require API 37.
        // targetSdk stays 36 so runtime behaviour is unchanged.
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "io.github.coderirse.reps"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Packages the exported schemas into androidTest assets so
        // MigrationTestHelper can create v1/v2 databases on device.
        testInstrumentationRunnerArgument("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

room {
    schemaDirectory("$projectDir/schemas")
}

// Privacy invariant (docs/PRODUCT.md section 8): Reps never declares INTERNET.
// Checked against the merged manifest so library manifests are covered too.
val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails if any merged manifest declares the INTERNET permission."
    dependsOn("processReleaseMainManifest")
    // Capture as plain File (config-cache safe); merged manifest dir name varies by AGP.
    val intermediatesDir = layout.buildDirectory.dir("intermediates").get().asFile
    outputs.upToDateWhen { false }
    doLast {
        val manifests = intermediatesDir.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && it.path.contains("merged_manifest") }
            .toList()
        if (manifests.isEmpty()) {
            throw GradleException("未找到 merged manifest，请先执行一次构建")
        }
        val offenders = manifests.filter { it.readText().contains("android.permission.INTERNET") }
        if (offenders.isNotEmpty()) {
            throw GradleException("检测到 INTERNET 权限，Reps 必须保持完全离线: $offenders")
        }
        println("隐私检查通过：${manifests.size} 个 merged manifest 均未声明 INTERNET 权限")
    }
}

tasks.named("check") { dependsOn(verifyNoInternetPermission) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
