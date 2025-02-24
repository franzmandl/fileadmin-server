package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.dto.ApplicationCtx
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail5")
class InodeIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @Test
    fun test0thAncestor() {
        val inode = requestCtx.getInode(SafePath("/depth/level0_1/level1_1/level2_1"))
        assertThat(Inode0.getNthAncestor(inode.inode0, 0).path).isEqualTo(SafePath("/"))
    }

    @Test
    fun test1thAncestor() {
        val inode = requestCtx.getInode(SafePath("/depth/level0_1/level1_1/level2_1"))
        assertThat(Inode0.getNthAncestor(inode.inode0, 1).path).isEqualTo(SafePath("/depth"))
    }

    private val expectedAncestors = listOf(
        SafePath("/"),
        SafePath("/depth"),
        SafePath("/depth/level0_1"),
        SafePath("/depth/level0_1/level1_1"),
        SafePath("/depth/level0_1/level1_1/level2_1"),
    )

    @Test
    fun testAncestors() {
        val inode = requestCtx.getInode(SafePath("/depth/level0_1/level1_1/level2_1"))
        assertThat(Inode0.getAncestors(inode.inode0).map { it.path }).containsExactlyElementsOf(expectedAncestors)
    }

    @Test
    fun testAncestorsBehindSymlink() {
        val inode = requestCtx.getInode(SafePath("/time/0000-00-00/level0_1/level1_1/level2_1"))
        assertThat(Inode0.getAncestors(inode.inode0).map { it.path }).containsExactlyElementsOf(expectedAncestors)
    }
}