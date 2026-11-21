plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "tea"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "tea.server.ApplicationKt"
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    implementation(libs.bouncycastle.bcprov)
    implementation(libs.nbvcxz)
    implementation(libs.angus.mail)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
}
