package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FilterCtxChangeIT(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private val path = SafePath("/example1/tags")
    private val requestCtx = applicationCtx.createRequestCtx(null)
    private final val filterCtx: FilterCtx

    init {
        val inode = requestCtx.getInode(path)
        assertThat(inode.config.errors).isEmpty()
        filterCtx = inode.config.filter!!.ctx
    }

    private fun filterItems(vararg filters: Filter) =
        filterCtx.filterItems(
            requestCtx,
            filterCtx.getLockedItemsList(requestCtx, CommandId.Add, TestUtil.failErrorHandler).toMutableList(),
            filters.toList(),
            TestUtil.failErrorHandler
        )

    @Test
    fun testMoveItem() {
        val original = Path.of("$jail/example1/input1/2022/2022-11-22 - #unknown1.txt")
        val renamed = Path.of("$jail/example1/input1/2022/2022-11-22 - #orphan.txt")
        if (original.exists()) {
            assertThat(filterItems().map(TestUtil::getItemPath)).containsExactlyInAnyOrderElementsOf(TestUtil.jail4Example1ItemPaths)
            original.moveTo(renamed)
            Thread.sleep(2)
        }
        assertThat(filterItems().map(TestUtil::getItemPath)).containsExactlyInAnyOrderElementsOf(
            TestUtil.jail4Example1ItemPaths - setOf(
                "/example1/input1/2022/2022-11-22 - #unknown1.txt"
            ) + setOf(
                "/example1/input1/2022/2022-11-22 - #orphan.txt"
            )
        )
        renamed.moveTo(original)
        Thread.sleep(2)
        assertThat(filterItems().map(TestUtil::getItemPath)).containsExactlyInAnyOrderElementsOf(TestUtil.jail4Example1ItemPaths)
    }
}