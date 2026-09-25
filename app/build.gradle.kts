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
        versionCode = 7
        versionName = "1.2.0"

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
        // 应用内更新要把服务端版本号与本机 VERSION_CODE 比较，需要 BuildConfig；
        // AGP 8+ 起默认不再生成它（项目此前没用到，所以一直没开）。
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// 隐私边界（docs/PRODUCT.md「隐私」一节）：Reps 会联网，但只做两件只读的事——
// 拉取云端题库、检查应用更新；用户数据（题库/进度/错题/笔记）永远不上传。
// 这条边界在这里被机器化强制，取代此前「禁止 INTERNET」的门禁（联网已是功能，
// 禁不掉；能守住的是"网络调用不许扩散到别处"）：
//   1. merged manifest 必须声明 INTERNET —— 云功能依赖它，被误删要立刻发现
//   2. 网络 API 只允许出现在 data/net 包内
//      （java.net.URLEncoder/URLDecoder/URI 是本地编解码，不在限制之列）
val verifyNetworkContainment = tasks.register("verifyNetworkContainment") {
    group = "verification"
    description = "Fails if network APIs leak outside data/net, or if INTERNET is missing."
    // 必须依赖 processReleaseManifest（最终合并产物）。只依赖
    // processReleaseMainManifest 拿到的是"还没合并库 manifest"的中间文件，
    // 且磁盘上那份可能是上一次构建留下的旧文件。
    dependsOn("processReleaseManifest")
    // Capture as plain File (config-cache safe); merged manifest dir name varies by AGP.
    val intermediatesDir = layout.buildDirectory.dir("intermediates").get().asFile
    val srcDir = file("src/main/java")
    // doLast 里不能访问 project（configuration cache 会直接报错），配置期先取出前缀
    val projectDirPath = projectDir.absolutePath
    outputs.upToDateWhen { false }
    doLast {
        val manifests = intermediatesDir.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && it.path.contains("merged_manifest") }
            .toList()
        if (manifests.isEmpty()) {
            throw GradleException("未找到 merged manifest，请先执行一次构建")
        }

        val missingInternet = manifests.filterNot {
            it.readText().contains("android.permission.INTERNET")
        }
        if (missingInternet.isNotEmpty()) {
            throw GradleException(
                "云端题库与应用内更新依赖 INTERNET 权限，但以下 manifest 未声明: $missingInternet"
            )
        }

        val networkApi = Regex(
            """\bokhttp3\b|\bjava\.net\.(?:URL|URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket|InetAddress|InetSocketAddress|SocketAddress)\b"""
        )
        val offenders = srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace('\\', '/').contains("/data/net/") }
            .mapNotNull { file ->
                val hit = file.readLines().firstOrNull { networkApi.containsMatchIn(it) }
                hit?.let {
                    val relative = file.absolutePath.removePrefix(projectDirPath).trimStart('\\', '/')
                    "$relative: ${it.trim()}"
                }
            }
            .toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "网络调用只允许出现在 data/net 包内，违规文件:\n" + offenders.joinToString("\n")
            )
        }
        println("隐私边界检查通过：INTERNET 已声明，网络调用仅存在于 data/net 包")
    }
}

tasks.named("check") { dependsOn(verifyNetworkContainment) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
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
    // 云端题库 + 应用内更新：项目里唯一允许发起网络请求的地方（data/net 包，
    // 由 verifyNetworkContainment 门禁守着）
    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
