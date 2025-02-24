package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.PathComponentVersion1
import com.franzmandl.fileadmin.dto.config.PathConditionVersion1
import com.franzmandl.fileadmin.dto.config.PathConditionVersioned
import com.franzmandl.fileadmin.dto.config.SimplePathConditionVersion1
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail1")
class PathConditionJail1IT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private fun getPaths(directory: String, condition: PathConditionVersioned, yieldInternal: Boolean): List<String> =
        applicationCtx.mapper.fromVersioned(condition, TestUtil.failErrorHandler).getSequenceOfDescendants(
            requestCtx.createPathFinderCtx(false),
            requestCtx.createPathFinderCtx(false).createPathFinder(SafePath(directory)).find(),
            PathCondition.Parameter(
                createPayload = { parent, inode, _ -> parent + inode.inode0.path.name },
                errorHandler = TestUtil.failErrorHandler,
                rootPayload = listOf<String>(),
                yieldInternal = yieldInternal
            ),
        ).map { TestUtil.visitedInodeToString(it) }.toList()

    @Test
    fun testVisitFiles1() {
        assertThat(getPaths("/", PathConditionVersion1(components = listOf(PathComponentVersion1(yield = true))), false)).containsExactlyInAnyOrder(
            "leaf:/a:a",
            "leaf:/b:b",
            "leaf:/c:c",
            "leaf:/errorDirectory:errorDirectory",
            "leaf:/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles1Internal() {
        assertThat(getPaths("/", PathConditionVersion1(components = listOf(PathComponentVersion1(yield = true))), true)).containsExactlyInAnyOrder(
            "internal:/",
            "leaf:/a:a",
            "leaf:/b:b",
            "leaf:/c:c",
            "leaf:/errorDirectory:errorDirectory",
            "leaf:/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles1Simple() {
        assertThat(getPaths("/", SimplePathConditionVersion1(minDepth = 1, maxDepth = 1), false)).containsExactlyInAnyOrder(
            "leaf:/a:a",
            "leaf:/b:b",
            "leaf:/c:c",
            "leaf:/errorDirectory:errorDirectory",
            "leaf:/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles2() {
        assertThat(getPaths("/a", PathConditionVersion1(components = listOf(PathComponentVersion1(yield = true))), false)).containsExactlyInAnyOrder(
            "leaf:/a/directory:directory",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles2Internal() {
        assertThat(getPaths("/a", PathConditionVersion1(components = listOf(PathComponentVersion1(yield = true))), true)).containsExactlyInAnyOrder(
            "internal:/a",
            "leaf:/a/directory:directory",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles2Simple() {
        assertThat(getPaths("/a", SimplePathConditionVersion1(minDepth = 1, maxDepth = 1), false)).containsExactlyInAnyOrder(
            "leaf:/a/directory:directory",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles3() {
        assertThat(
            getPaths(
                "/a", PathConditionVersion1(
                    components = listOf(
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true)
                    ),
                    ignoreNonDirectories = true
                ),
                false
            )
        ).containsExactlyInAnyOrder(
            "leaf:/a/directory:directory",
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles3Internal() {
        assertThat(
            getPaths(
                "/a", PathConditionVersion1(
                    components = listOf(
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true)
                    ),
                    ignoreNonDirectories = true
                ),
                true
            )
        ).containsExactlyInAnyOrder(
            "internal:/a",
            "leaf:/a/directory:directory",
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles3Simple() {
        assertThat(getPaths("/a", SimplePathConditionVersion1(minDepth = 1, maxDepth = 2), false)).containsExactlyInAnyOrder(
            "leaf:/a/directory:directory",
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "leaf:/a/errorDirectory:errorDirectory",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
            "leaf:/a/errorFile:errorFile",
        )
    }

    @Test
    fun testVisitFiles4() {
        assertThat(
            getPaths(
                "/a",
                PathConditionVersion1(
                    components = listOf(
                        PathComponentVersion1(),
                        PathComponentVersion1(yield = true)
                    ),
                    ignoreNonDirectories = true
                ),
                false
            )
        ).containsExactlyInAnyOrder(
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
        )
    }

    @Test
    fun testVisitFiles4Internal() {
        assertThat(
            getPaths(
                "/a",
                PathConditionVersion1(
                    components = listOf(
                        PathComponentVersion1(),
                        PathComponentVersion1(yield = true)
                    ),
                    ignoreNonDirectories = true
                ),
                true
            )
        ).containsExactlyInAnyOrder(
            "internal:/a",
            "internal:/a/directory",
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "internal:/a/errorDirectory",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
            "internal:/a/errorFile",
        )
    }

    @Test
    fun testVisitFiles4Simple() {
        assertThat(
            getPaths(
                "/a",
                SimplePathConditionVersion1(minDepth = 2, maxDepth = 2),
                false
            )
        ).containsExactlyInAnyOrder(
            "leaf:/a/directory/aDirectory:directory/aDirectory",
            "leaf:/a/directory/aFile:directory/aFile",
            "leaf:/a/errorDirectory/.gitkeep:errorDirectory/.gitkeep",
        )
    }
}