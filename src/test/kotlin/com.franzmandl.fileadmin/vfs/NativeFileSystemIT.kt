package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.dto.ApplicationCtx
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail3")
class NativeFileSystemIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @Test
    fun `test symlink02`() {
        val inode = requestCtx.getInode(SafePath("/directory01/symlink02"))
        assertThat(getTarget(inode.inode0)).isEqualTo(SafePath("/directory01"))
        assertThat(inode.config.samePaths).containsExactlyInAnyOrder(SafePath("/directory01/symlink02"))
    }

    @Test
    fun `test filterJail4`() {
        val inode = requestCtx.getInode(SafePath("/filterJail4"))
        assertThat(getTarget(inode.inode0)).isNull()
        assertThat(inode.config.samePaths).isEmpty()
    }

    @Test
    fun `test symlink03`() {
        val inode = requestCtx.getInode(SafePath("/symlink03"))
        assertThat(inode.inode0.path).isEqualTo(SafePath("/symlink03"))
        assertThat(getTarget(inode.inode0)).isEqualTo(SafePath("/"))
        assertThat(inode.config.samePaths).containsExactlyInAnyOrder(SafePath("/symlink03"))
    }

    @Test
    fun `test symlink`() {
        val inode = requestCtx.getInode(SafePath("/tasksJail2/symlink"))
        assertThat(inode.inode0.path).isEqualTo(SafePath("/tasksJail2/symlink"))
        assertThat(getTarget(inode.inode0)).isEqualTo(SafePath("/tasksJail2/date/60-done/2020-01-02 - 60-dummy"))
        assertThat(inode.config.samePaths).containsExactlyInAnyOrder(SafePath("/tasksJail2/symlink"))
    }

    @Test
    fun `test 40-to_do`() {
        val inode = requestCtx.getInode(SafePath("/tasksJail2/symlink/40-to_do"))
        assertThat(inode.inode0.path).isEqualTo(SafePath("/tasksJail2/date/60-done/2020-01-02 - 60-dummy/40-to_do"))
        assertThat(inode.config.samePaths).containsExactlyInAnyOrder(SafePath("/tasksJail2/symlink/40-to_do"))
    }

    private fun getTarget(inode: Inode0): SafePath? =
        if (inode is Link) inode.targetInode.inode0.path else null
}