package com.franzmandl.fileadmin.common

import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail1")
class CommonUtilTests(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.paths.jail}") private val jail: String,
) {
    private val requestCtx = applicationCtx.createRequestCtx()

    private fun onError(message: String) {
        fail<Nothing>("An error occurred: $message")
    }

    private fun getPaths(directory: String, minDepth: Int, maxDepth: Int): Set<String> =
        CommonUtil.getSequenceOfDescendants(
            requestCtx.createPathFinderCtx(),
            requestCtx.createPathFinderCtx().createPathFinder(SafePath(directory)).find(),
            minDepth,
            maxDepth,
            setOf(),
            ::onError,
        ).mapTo(mutableSetOf()) { it.toString() }

    @Test
    fun testVisitFiles1() {
        assertThat(getPaths("/", 0, 0)).containsExactlyInAnyOrder(
            "$jail/a",
            "$jail/b",
            "$jail/c",
            "$jail/errorDirectory",
            "$jail/errorFile",
        )
        assertThat(getPaths("/a", 0, 0)).containsExactlyInAnyOrder(
            "$jail/a/directory",
            "$jail/a/errorDirectory",
            "$jail/a/errorFile",
        )
        assertThat(getPaths("/a", 0, 1)).containsExactlyInAnyOrder(
            "$jail/a/directory",
            "$jail/a/directory/aDirectory",
            "$jail/a/directory/aFile",
            "$jail/a/errorDirectory",
            "$jail/a/errorDirectory/.gitkeep",
            "$jail/a/errorFile",
        )
        assertThat(getPaths("/a", 1, 1)).containsExactlyInAnyOrder(
            "$jail/a/directory/aDirectory",
            "$jail/a/directory/aFile",
            "$jail/a/errorDirectory/.gitkeep",
        )
    }

    @Test
    fun testGetPartsList() {
        assertThat(CommonUtil.getPartsList(listOf<String>(), true)).isEqualTo(listOf(listOf<String>()))
        assertThat(CommonUtil.getPartsList(listOf("a"), true)).isEqualTo(listOf(listOf(), listOf("a")))
        assertThat(CommonUtil.getPartsList(listOf(" "), true)).isEqualTo(listOf(listOf(), listOf(" ")))
    }
}