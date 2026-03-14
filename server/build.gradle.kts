plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "pl.dawidszczesniak.blockchain_platform"
version = "1.0.0"
application {
    mainClass.set("pl.dawidszczesniak.blockchain_platform.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverDefaultHeaders)
    implementation(libs.ktor.serverSessions)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverNetty)
    implementation(libs.postgresql)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.web3j.crypto)
    implementation(libs.jedis)
    implementation(libs.koin.ktor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

tasks.withType<JavaExec>().configureEach {
    val appEnv = (project.findProperty("appEnv") as String?)?.trim()
    if (!appEnv.isNullOrEmpty()) {
        environment("APP_ENV", appEnv)
    }
}
