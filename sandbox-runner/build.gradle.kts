import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "pl.dawidszczesniak.blockchain_platform"
version = "1.0.0"

application {
    mainClass.set("pl.dawidszczesniak.blockchain_platform.sandboxrunner.SandboxRunnerKt")
    applicationDefaultJvmArgs = listOf("--add-modules=jdk.httpserver")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

dependencies {
    testImplementation(libs.kotlin.testJunit)
}
