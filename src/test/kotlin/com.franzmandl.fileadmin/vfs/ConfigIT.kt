package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.CommandId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting

@SpringBootTest
@ActiveProfiles("test", "jail6")
class ConfigIT(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @Test
    fun testImportedNewInodeTemplate1() {
        val inode = requestCtx.getInode(SafePath("/config/dir2"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(inode.config.newInodeTemplate.isFile).isTrue()
        assertThat(inode.config.newInodeTemplate.name).isEqualTo("")
    }

    @Test
    fun testImportedNewInodeTemplate2() {
        val inode = requestCtx.getInode(SafePath("/config/dir4"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(inode.config.newInodeTemplate.isFile).isTrue()
        assertThat(inode.config.newInodeTemplate.name).isEqualTo("")
    }

    @Test
    fun testImportBrokenSymbolicLink() {
        val brokenSymbolicLink = Path.of("$jail/config/dir5/input1/brokenSymbolicLink")
        val outputSymbolicLink = Path.of("$jail/config/dir5/input1/tags")
        try {
            Files.createSymbolicLink(brokenSymbolicLink, Path.of("doesNotExist"))
            Files.createSymbolicLink(outputSymbolicLink, Path.of("../tags"))
            val inode = requestCtx.getInode(SafePath("/config/dir5"))
            assertThat(inode.config.errors).containsExactly(
                """/config/dir5/input1/brokenSymbolicLink/doesNotExist: Cannot be imported.""",
                """Input "/config/dir5/input1/brokenSymbolicLink/doesNotExist/doesNotExist" not available.""",
            )
            assertThat(inode.children).containsExactlyInAnyOrderElementsOf(setOf(".fileadmin.json", "config", "input1", "tags").map { SafePath("/config/dir5/$it") })
            inode.config.filter!!.ctx.scanItems(requestCtx, CommandId.Add, ErrorHandler.noop)
        } finally {
            brokenSymbolicLink.deleteExisting()
            outputSymbolicLink.deleteExisting()
        }
    }
}