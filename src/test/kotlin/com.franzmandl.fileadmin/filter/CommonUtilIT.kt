package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.SimplePathConditionVersion1
import com.franzmandl.fileadmin.vfs.PathCondition
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail4")
class CommonUtilIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private fun getSequenceOfTimeBasedDescendants(yieldInternal: Boolean): List<SafePath> =
        applicationCtx.mapper.fromVersioned(SimplePathConditionVersion1(pruneNames = setOf("pruneName"), time = true), TestUtil.failErrorHandler).getSequenceOfDescendants(
            requestCtx.createPathFinderCtx(false),
            requestCtx.getInode(SafePath("/example3/input2/level1")),
            PathCondition.Parameter(
                createPayload = PathCondition.Parameter.createNullPayload,
                errorHandler = TestUtil.failErrorHandler,
                rootPayload = null,
                yieldInternal = yieldInternal
            ),
        )
            .mapTo(mutableListOf()) { it.inode1.inode0.path }

    @Test
    fun testGetSequenceOfTimeBasedDescendants() {
        assertThat(getSequenceOfTimeBasedDescendants(false)).containsExactly(
            SafePath("/example3/input2/level1/2001/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001-01-01 - item.txt"),
        )
    }

    @Test
    fun testGetSequenceOfTimeBasedDescendantsYieldSelf() {
        assertThat(getSequenceOfTimeBasedDescendants(true)).containsExactly(
            SafePath("/example3/input2/level1"),
            SafePath("/example3/input2/level1/2001"),
            SafePath("/example3/input2/level1/2001/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001/2001-01"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001-01-01 - item.txt"),
        )
    }
}