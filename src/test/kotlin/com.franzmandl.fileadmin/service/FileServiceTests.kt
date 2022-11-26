package com.franzmandl.fileadmin.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileServiceTests {
    private val fileService: FileService = FileService("./src/test/resources")

    @Test
    fun glob1() {
        val foundList = fileService.glob("/jail1/*/dir")
        assertThat(foundList).hasSize(6)
        val foundSet = foundList.map { it.path }.toSet()
        assertThat(foundSet).isEqualTo(
            setOf(
                "./src/test/resources/jail1/a/dir/aDir",
                "./src/test/resources/jail1/a/dir/aFile",
                "./src/test/resources/jail1/b/dir/bDir",
                "./src/test/resources/jail1/b/dir/bFile",
                "./src/test/resources/jail1/c/dir/cDir",
                "./src/test/resources/jail1/c/dir/cFile"
            )
        )
    }

    @Test
    fun glob2() {
        val foundList = fileService.glob("/jail1/*")
        assertThat(foundList).hasSize(10)
        val foundSet = foundList.map { it.path }.toSet()
        assertThat(foundSet).isEqualTo(
            setOf(
                "./src/test/resources/jail1/a/dir",
                "./src/test/resources/jail1/a/errorDir",
                "./src/test/resources/jail1/a/errorFile",
                "./src/test/resources/jail1/b/dir",
                "./src/test/resources/jail1/b/errorDir",
                "./src/test/resources/jail1/b/errorFile",
                "./src/test/resources/jail1/c/dir",
                "./src/test/resources/jail1/c/errorDir",
                "./src/test/resources/jail1/c/errorFile",
                "./src/test/resources/jail1/errorDir/.gitkeep"
            )
        )
    }
}
