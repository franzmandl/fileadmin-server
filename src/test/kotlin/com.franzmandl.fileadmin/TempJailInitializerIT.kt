package com.franzmandl.fileadmin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.nio.file.Path
import kotlin.io.path.writeText

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [TempJailInitializer::class])
@DirtiesContext
class TempJailInitializerIT(
    @Value("\${application.jail.path}") private val jail: Path,
) {
    @BeforeEach
    fun beforeEach() {
        assertThat(jail).isEqualTo(TempJailInitializer.jail)
        TempJailInitializer.beforeEach()
    }

    @Test
    fun testCleanupJailInitializer1() {
        assertThat(jail).isEmptyDirectory()
        jail.resolve("test1.txt").writeText("1")
    }

    @Test
    fun testCleanupJailInitializer2() {
        assertThat(jail).isEmptyDirectory()
        jail.resolve("test2.txt").writeText("2")
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll(@TempDir tempDir: Path) {
            TempJailInitializer.beforeAll(tempDir)
        }
    }
}