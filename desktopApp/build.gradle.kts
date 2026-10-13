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
        val appVersion = providers.gradleProperty("tarang.version").get()
        jvmArgs += listOf("-Xmx1g", "-Dtarang.version=$appVersion")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            modules("java.desktop", "java.instrument", "java.management", "java.net.http", "java.sql", "java.logging", "java.naming", "jdk.unsupported", "jdk.crypto.ec", "jdk.accessibility")
            packageName = "Tarang"
            packageVersion = providers.gradleProperty("tarang.version").get()
            macOS {
                bundleID = "com.westtiwan.tarang"
                dockName = "Tarang"
            }
        }
    }
}

// Development log for `gradle run`, never set in packaged builds.
tasks.withType<JavaExec>().configureEach {
    if (name == "run") systemProperty("tarang.debugLog", rootProject.file(".devloop/app.log").absolutePath)
    systemProperty("tarang.version", providers.gradleProperty("tarang.version").get())
}
