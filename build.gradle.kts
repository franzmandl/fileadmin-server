import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.antlr:antlr4:4.10.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.tika:tika-core:2.3.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.assertj:assertj-core")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

task<JavaExec>("customGenerateGrammarSource") {
    mainClass.set("org.antlr.v4.Tool")
    args = listOf(
        "-no-listener",
        "-package",
        "com.franzmandl.fileadmin.generated.ticket",
        "-o",
        "${project.projectDir}/src/main/java/com/franzmandl/fileadmin/generated/ticket",
        "${project.projectDir}/src/main/antlr/Ticket.g4"
    )
    classpath = configurations.compileClasspath.get()
}

task("customGenerateSource") {
    dependsOn("customGenerateGrammarSource")
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
