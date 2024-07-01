import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    kotlin("jvm") version "2.0.0"
    id("net.ltgt.errorprone") version "4.0.1"
}

group = "com.babylonml"
version = "1.0-SNAPSHOT"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Existing dependencies
    implementation("org.apache.commons:commons-rng-core:1.5")
    implementation("org.apache.commons:commons-rng-simple:1.5")
    implementation("org.apache.commons:commons-rng-sampling:1.5")
    implementation("net.openhft:zero-allocation-hashing:0.26ea0")
    implementation("it.unimi.dsi:fastutil:8.5.13")
    implementation("org.jspecify:jspecify:0.3.0")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.junit.jupiter:junit-jupiter")

    errorprone("com.google.errorprone:error_prone_core:2.28.0")
    errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile> {
    // Add module for Vector API if available and necessary
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))

    if (!name.lowercase().contains("test")) {
        options.errorprone {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "com.babylonml")
        }
    }
}

tasks {
    test {
        // Use JUnit Platform and enable preview features in tests
        useJUnitPlatform()
        jvmArgs = listOf("-Xmx8g", "--add-modules", "jdk.incubator.vector")
    }

    register<JavaExec>("mnistBench") {
        group = "application"
        mainClass = "com.babylonml.backend.examples.mnist.MNISTBench"
        classpath = sourceSets["main"].runtimeClasspath
        jvmArgs = listOf(
            "-server",
            "-Xmx16g",
            "-XX:MaxDirectMemorySize=110g",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "--add-modules",
            "jdk.incubator.vector",
            "-Djava.awt.headless=true"
        )
    }

    register<JavaExec>("mnistBenchLight") {
        group = "application"
        mainClass = "com.babylonml.backend.examples.mnist.MNISTBenchLight"
        classpath = sourceSets["main"].runtimeClasspath
        jvmArgs = listOf(
            "-server",
            "-Xmx16g",
            "-XX:MaxDirectMemorySize=110g",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "--add-modules",
            "jdk.incubator.vector",
            "-Djava.awt.headless=true"
        )
    }


    register<JavaExec>("makemore") {
        group = "application"
        mainClass = "com.tornadoml.makemore.Makemore"
        classpath = sourceSets["main"].runtimeClasspath
        jvmArgs = listOf(
            "-server",
            "-Xmx16g",
            "--add-modules",
            "jdk.incubator.vector",
        )
    }
}
