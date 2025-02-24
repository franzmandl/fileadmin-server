import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

plugins {
    id("io.spring.dependency-management") version "1.1.6" // see https://mvnrepository.com/artifact/io.spring.dependency-management/io.spring.dependency-management.gradle.plugin
    id("org.springframework.boot") version "3.3.3" // see https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-test
    val kotlinVersion = "2.0.20"
    kotlin("jvm") version kotlinVersion // see https://mvnrepository.com/artifact/org.jetbrains.kotlin.jvm/org.jetbrains.kotlin.jvm.gradle.plugin
    kotlin("plugin.jpa") version kotlinVersion // see https://mvnrepository.com/artifact/org.jetbrains.kotlin.plugin.jpa/org.jetbrains.kotlin.plugin.jpa.gradle.plugin
    kotlin("plugin.serialization") version kotlinVersion // see https://mvnrepository.com/artifact/org.jetbrains.kotlin.plugin.serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin
    kotlin("plugin.spring") version kotlinVersion // see https://mvnrepository.com/artifact/org.jetbrains.kotlin.plugin.spring/org.jetbrains.kotlin.plugin.spring.gradle.plugin
    application
}

group = "com.franzmandl"
version = "1.3.0"
java.sourceCompatibility = JavaVersion.VERSION_17
val kotlinCompilerOptionJvmTarget = JvmTarget.JVM_17

repositories {
    mavenCentral()
}

dependencies {
    // URL format: https://mvnrepository.com/artifact/$groupId/$artifactId
    implementation("org.antlr:antlr4:4.13.2") // see https://mvnrepository.com/artifact/org.antlr/antlr4
    implementation("org.apache.commons:commons-imaging:1.0.0-alpha5") // see https://mvnrepository.com/artifact/org.apache.commons/commons-imaging
    implementation("org.apache.commons:commons-lang3:3.17.0") // see https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    implementation("org.apache.pdfbox:pdfbox-tools:3.0.3") // see https://mvnrepository.com/artifact/org.apache.pdfbox/pdfbox-tools
    implementation("org.apache.tika:tika-core:2.9.2") // see https://mvnrepository.com/artifact/org.apache.tika/tika-core
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2") // see https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        // see https://kotlinlang.org/docs/gradle-compiler-options.html#all-compiler-options
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = kotlinCompilerOptionJvmTarget
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootBuildImage {
    builder.set("paketobuildpacks/builder-jammy-base:latest")
}

// Adapted from https://github.com/AbhyudayaSharma/react-git-info
task("customGenerateGitInfoSource") {
    val generatedDirectory = Path.of("${project.projectDir}/src/main/java/com/franzmandl/fileadmin/generated")
    generatedDirectory.createDirectories()
    val gitLogOutputStream = ByteArrayOutputStream()
    project.exec {
        commandLine = "git log --format=%D%n%h%n%H%n%cI%n%B -n 1 HEAD --".split(" ")
        standardOutput = gitLogOutputStream
    }
    val gitLogResult = gitLogOutputStream.toString().split(Regex("\r?\n"))
    val refs = gitLogResult[0].split(", ")
    val shortHash = gitLogResult[1]
    val hash = gitLogResult[2]
    val date = gitLogResult[3]
    var branch: String? = null
    val tags = mutableListOf<String>()
    for (ref in refs) {
        branch = Regex("""^HEAD -> (.*)$""").matchEntire(ref)?.groups?.get(1)?.value ?: branch
        Regex("""^tag: (.*)$""").matchEntire(ref)?.groups?.get(1)?.value?.let(tags::add)
    }
    fun quote(string: String?) = if (string != null) "\"$string\"" else "null"
    generatedDirectory.resolve("GitInfo.java").writeText(
        "package com.franzmandl.fileadmin.generated;" +
                "\n" +
                "\npublic class GitInfo {" +
                "\n\tpublic static final String branch = " + quote(branch) + ";" +
                "\n\tpublic static final String shortHash = " + quote(shortHash) + ";" +
                "\n\tpublic static final String hash = " + quote(hash) + ";" +
                "\n\tpublic static final String date = " + quote(date) + ";" +
                "\n\tpublic static final String[] tags = new String[] {" + tags.joinToString(", ") { quote(it) } + "};" +
                "\n}" +
                "\n"
    )
}

task<JavaExec>("customGenerateGrammarSource") {
    mainClass.set("org.antlr.v4.Tool")
    args = listOf(
        "-no-listener",
        "-package",
        "com.franzmandl.fileadmin.generated.task",
        "-o",
        "${project.projectDir}/src/main/java/com/franzmandl/fileadmin/generated/task",
        "${project.projectDir}/src/main/antlr/Task.g4"
    )
    classpath = configurations.compileClasspath.get()
}

task("customGenerateSource") {
    dependsOn("customGenerateGitInfoSource", "customGenerateGrammarSource")
}

tasks.named("compileKotlin") {
    dependsOn("customGenerateSource")
}

task("customClean") {
    Files.walk(Path.of("${project.projectDir}/src"))
        .filter { it.isDirectory() }
        .sorted(Comparator.reverseOrder()) // To also delete directories becoming empty.
        .filter { it.useDirectoryEntries { sequence -> sequence.firstOrNull() } == null }
        .forEach { it.deleteExisting() }
}

tasks.named("clean") {
    dependsOn("customClean")
}

application {
    mainClass.set("com.franzmandl.fileadmin.ServerKt")
}

springBoot {
    mainClass.set("com.franzmandl.fileadmin.ServerKt")
}
