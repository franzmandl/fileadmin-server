package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.PathComponentVersion1
import com.franzmandl.fileadmin.dto.config.PathConditionVersion1
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail5")
class PathConditionJail5IT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private fun getPaths(directory: String, condition: PathConditionVersion1, yieldInternal: Boolean): List<String> =
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
    fun testVisitFilesDepth1() {
        assertThat(
            getPaths(
                "/depth", PathConditionVersion1(
                    listOf(
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true),
                    )
                ), false
            )
        ).containsExactlyInAnyOrder(
            "leaf:/depth/level0_0:level0_0",
            "leaf:/depth/level0_0/level1_0:level0_0/level1_0",
            "leaf:/depth/level0_0/level1_0/level2_0:level0_0/level1_0/level2_0",
            "leaf:/depth/level0_0/level1_0/level2_1:level0_0/level1_0/level2_1",
            "leaf:/depth/level0_0/level1_1:level0_0/level1_1",
            "leaf:/depth/level0_0/level1_1/level2_0:level0_0/level1_1/level2_0",
            "leaf:/depth/level0_0/level1_1/level2_1:level0_0/level1_1/level2_1",
            "leaf:/depth/level0_1:level0_1",
            "leaf:/depth/level0_1/level1_0:level0_1/level1_0",
            "leaf:/depth/level0_1/level1_0/level2_0:level0_1/level1_0/level2_0",
            "leaf:/depth/level0_1/level1_0/level2_1:level0_1/level1_0/level2_1",
            "leaf:/depth/level0_1/level1_1:level0_1/level1_1",
            "leaf:/depth/level0_1/level1_1/level2_0:level0_1/level1_1/level2_0",
            "leaf:/depth/level0_1/level1_1/level2_1:level0_1/level1_1/level2_1",
        )
    }

    @Test
    fun testVisitFilesDepth1Internal() {
        assertThat(
            getPaths(
                "/depth", PathConditionVersion1(
                    listOf(
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true),
                        PathComponentVersion1(yield = true),
                    )
                ), true
            )
        ).containsExactlyInAnyOrder(
            "internal:/depth",
            "leaf:/depth/level0_0:level0_0",
            "leaf:/depth/level0_0/level1_0:level0_0/level1_0",
            "leaf:/depth/level0_0/level1_0/level2_0:level0_0/level1_0/level2_0",
            "leaf:/depth/level0_0/level1_0/level2_1:level0_0/level1_0/level2_1",
            "leaf:/depth/level0_0/level1_1:level0_0/level1_1",
            "leaf:/depth/level0_0/level1_1/level2_0:level0_0/level1_1/level2_0",
            "leaf:/depth/level0_0/level1_1/level2_1:level0_0/level1_1/level2_1",
            "leaf:/depth/level0_1:level0_1",
            "leaf:/depth/level0_1/level1_0:level0_1/level1_0",
            "leaf:/depth/level0_1/level1_0/level2_0:level0_1/level1_0/level2_0",
            "leaf:/depth/level0_1/level1_0/level2_1:level0_1/level1_0/level2_1",
            "leaf:/depth/level0_1/level1_1:level0_1/level1_1",
            "leaf:/depth/level0_1/level1_1/level2_0:level0_1/level1_1/level2_0",
            "leaf:/depth/level0_1/level1_1/level2_1:level0_1/level1_1/level2_1",
        )
    }

    @Test
    fun testVisitFilesTime() {
        assertThat(
            getPaths(
                "/time", PathConditionVersion1(
                    listOf(
                        PathComponentVersion1(time = true, yield = true),
                        PathComponentVersion1(yield = true),
                    )
                ), false
            )
        ).containsExactlyInAnyOrder(
            "leaf:/time/2001/2001-00-00:2001-00-00",
            "leaf:/depth/level0_0:2001-00-00/level0_0",
            "leaf:/depth/level0_1:2001-00-00/level0_1",
            "leaf:/time/2001/2001-01/2001-01-01:2001-01-01",
            "leaf:/depth/level0_0:2001-01-01/level0_0",
            "leaf:/depth/level0_1:2001-01-01/level0_1",
            "leaf:/time/0000-00-00:0000-00-00",
            "leaf:/depth/level0_0:0000-00-00/level0_0",
            "leaf:/depth/level0_1:0000-00-00/level0_1"
        )
    }
}