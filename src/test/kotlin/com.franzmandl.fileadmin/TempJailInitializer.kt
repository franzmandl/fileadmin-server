package com.franzmandl.fileadmin

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteRecursively

/**
 * Inspired by https://stackoverflow.com/questions/58289509/in-spring-boot-test-how-do-i-map-a-temporary-folder-to-a-configuration-property
 */
class TempJailInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            mapOf(
                "application.jail.path" to jail.toString(),
            )
        ).applyTo(applicationContext)
    }

    companion object {
        lateinit var jail: Path

        fun beforeAll(tempDir: Path) {
            jail = tempDir.resolve("jail").createDirectory()
        }

        fun beforeEach() {
            @OptIn(ExperimentalPathApi::class)
            jail.deleteRecursively()
            jail.createDirectory()
        }
    }
}