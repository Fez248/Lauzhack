plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // wasm tests run in headless Chrome via Karma (Node can't load Compose's Skiko runtime).
            // No Chrome installed? Point CHROME_BIN at any Chromium browser, e.g. Edge:
            //   export CHROME_BIN="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        // Required by the Compose plugin so wasm tests bundle the Skiko runtime (CMP-4906).
        binaries.executable()
    }

    // Android target goes here later (conditional on an Android SDK being present).

    sourceSets {
        commonMain.dependencies {
            // `api`: these types appear in shared's public API (composables, models), so app shells
            // and the server get them transitively.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            // Skiko native runtime, for off-screen rendering in tests (tea.ui.Screenshots).
            implementation(compose.desktop.currentOs)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "tea.generated.resources"
    generateResClass = always
}
