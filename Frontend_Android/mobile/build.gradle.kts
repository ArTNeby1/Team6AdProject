import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.owasp.dependencycheck")
}

// SCA：跟 backend 的 dependency-check-maven 同一个数据源/规则，NVD_API_KEY 走同一个
// GitHub secret。JSON 报告方便和 backend 那边的 CI 步骤保持一致（都上传成 artifact）。
dependencyCheck {
    // SARIF 给 GitHub Code Scanning 用（Security 标签页持续追踪 open/resolved）
    formats = listOf("JSON", "HTML", "SARIF")
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""


    scanConfigurations = listOf("implementation", "debugImplementation")
    skipTestGroups = true
}

android {
    namespace = "com.loomytrip.mobile"
    compileSdk = 35

    val backendBaseUrl = providers.gradleProperty("BACKEND_BASE_URL")
        .orElse("http://ad-project-dev-593875640.ap-southeast-1.elb.amazonaws.com/")

    defaultConfig {
        applicationId = "com.loomytrip.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"${backendBaseUrl.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // lintDebug 本来就在跑（见 ci-android.yml 的 SAST 步骤），只是结果只传成了普通
    // artifact，没接进 Code Scanning。AGP 4.2+ 原生支持 Lint 输出 SARIF，不用额外
    // 转换工具——同一次分析多出一种格式。不写 sarifOutput 时默认落在
    // build/reports/lint-results-debug.sarif，跟 ci-android.yml 里引用的路径一致。
    lint {
        sarifReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
