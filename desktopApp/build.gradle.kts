import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.mp)
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.jflac)
    implementation(libs.jlayer)
}

compose.desktop {
    application {
        mainClass = "com.westtiwan.tarang.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            modules("java.net.http", "java.desktop", "java.sql", "jdk.unsupported")
            packageName = "Tarang"
            packageVersion = "1.0.0"
            macOS {
                bundleID = "com.westtiwan.tarang"
                dockName = "Tarang"
            }
        }
    }
}
