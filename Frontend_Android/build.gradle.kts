plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // SCA：跟 backend/pom.xml 的 dependency-check-maven 是同一个 OWASP 项目，
    // 风格/报告格式保持一致，实际 apply 在 mobile 模块里。
    id("org.owasp.dependencycheck") version "13.0.0" apply false
}
