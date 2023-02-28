import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    id("org.springframework.boot") version "2.7.3"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.jpa") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
    application
}

group = "com.franzmandl"
version = "1.1.0"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.antlr:antlr4:4.11.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.tika:tika-core:2.6.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

// Adapted from https://github.com/AbhyudayaSharma/react-git-info
task("customGenerateGitInfoSource") {
    val generatedDirectory = File("${project.projectDir}/src/main/java/com/franzmandl/fileadmin/generated")
    generatedDirectory.mkdirs()
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
        branch = Regex("^HEAD -> (.*)\$").matchEntire(ref)?.groups?.get(1)?.value ?: branch
        Regex("^tag: (.*)\$").matchEntire(ref)?.groups?.get(1)?.value?.let(tags::add)
    }
    fun quote(string: String?) = if (string != null) "\"$string\"" else "null"
    File(generatedDirectory, "GitInfo.java").writeText(
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

application {
    mainClass.set("com.franzmandl.fileadmin.ServerKt")
}

springBoot {
    mainClass.set("com.franzmandl.fileadmin.ServerKt")
}
