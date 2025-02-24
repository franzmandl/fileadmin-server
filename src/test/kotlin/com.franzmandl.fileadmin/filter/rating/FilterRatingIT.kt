package com.franzmandl.fileadmin.filter.rating

import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.filter.ItemDtoHelper
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.stream.Stream

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FilterRatingIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @ParameterizedTest(name = """"{0}", "{1}"""")
    @MethodSource
    fun test(filterPath: String, itemPath: String, expected: Int) {
        val inode = requestCtx.getInode(SafePath(filterPath))
        val filter = inode.config.filter!!
        val filterResult = inode.config.filterResult!!
        val item = filter.ctx.getItem(SafePath(itemPath))
        assertThat(ItemDtoHelper.createItemDto(filter.ctx.output, item, filterResult, null).result!!.priority).isEqualTo(expected)
    }

    @Test
    fun testInvestigate() {
        test("/example1/tags/phone1", "/example1/input1/2022/2022-11-22 - content", 10)
    }

    companion object {
        @JvmStatic
        fun test(): Stream<Arguments> =
            Stream.of(
                Arguments.of("/example5/tags", "/example5/input1/2001/2001-01-01 - item", 0),
                Arguments.of("/example5/tags/input1_input", "/example5/input1/2001/2001-01-01 - item", 10),
                Arguments.of("/example5/tags/,tagReason/Automatic/input1_input", "/example5/input1/2001/2001-01-01 - item", 10),
                Arguments.of("/example5/tags/,tagRelationship/Self/input1_input", "/example5/input1/2001/2001-01-01 - item", 20),
                Arguments.of("/example1/tags/phone1", "/example1/input1/2022/2022-11-22 - content", 10),
                Arguments.of("/example1/tags/phone1", "/example1/input2/#phone1.js", 10),
                Arguments.of("/example1/tags/,tagReason/Name/phone1", "/example1/input1/2022/2022-11-22 - content", 0),
                Arguments.of("/example1/tags/,tagReason/Name/phone1", "/example1/input2/#phone1.js", 10),
                Arguments.of("/example1/tags/,tagReason/Name/,tagRelationship/Self/phone1", "/example1/input2/#phone1.js", 20),
                Arguments.of("/example6/tags/A", "/example6/input1/#A#A#A.txt", 30), // This is an unlikely tagged item.
                Arguments.of("/example6/tags/A", "/example6/input1/#A#B#C.txt", 10),
                Arguments.of("/example6/tags/A", "/example6/input1/#A #A #A.txt", 10),
                Arguments.of("/example6/tags/A/B", "/example6/input1/#A#A#A.txt", 30), // This is an unlikely tagged item.
                Arguments.of("/example6/tags/A/B", "/example6/input1/#A#B#C.txt", 20),
                Arguments.of("/example6/tags/A/B", "/example6/input1/#A #A #A.txt", 10),
                Arguments.of("/example6/tags/A/B/C", "/example6/input1/#A#A#A.txt", 30), // This is an unlikely tagged item.
                Arguments.of("/example6/tags/A/B/C", "/example6/input1/#A#B#C.txt", 30),
                Arguments.of("/example6/tags/A/B/C", "/example6/input1/#A #A #A.txt", 10),
                Arguments.of("/example6/tags/B/C", "/example6/input1/#A#A#A.txt", 0),
                Arguments.of("/example6/tags/B/C", "/example6/input1/#A#B#C.txt", 20),
                Arguments.of("/example6/tags/B/C", "/example6/input1/#A #A #A.txt", 0),
                Arguments.of("/example6/tags/A/C", "/example6/input1/#A#B#C.txt", 20),
                Arguments.of("/example6/tags/A/B/C", "/example6/input1/#AA#BA#CA.txt", 18),
                Arguments.of("/example6/tags/A/AA/B/BB/C/CC", "/example6/input1/#AA#BA#CA.txt", 28),
            )
    }
}