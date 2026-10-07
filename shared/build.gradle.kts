import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.mp)
}

kotlin {
    android {
        namespace = "com.westtiwan.tarang.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.icons.extended)
            api(libs.coroutines.core)
            api(libs.serialization.json)
            api(libs.ktor.core)
            implementation(libs.ktor.okhttp)
            api(libs.coil.compose)
            implementation(libs.coil.ktor)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kuromoji)
            }
        }
        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.core.ktx)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                implementation(libs.icu4j)
            }
        }
    }
}
