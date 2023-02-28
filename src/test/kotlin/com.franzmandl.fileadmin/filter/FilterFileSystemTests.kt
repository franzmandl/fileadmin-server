package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.model.ApplicationCtx
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
class FilterFileSystemTests(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.paths.jail}") private val jail: String,
) {
    private val requestCtx = applicationCtx.createRequestCtx()

    private val rootFilterNames = setOf(
        SafePath("/example1/tags/,evaluate"),
        SafePath("/example1/tags/,operator"),
        SafePath("/example1/tags/!lostAndFound"),
        SafePath("/example1/tags/!unknown"),
        SafePath("/example1/tags/.fileadmin.system"),
        SafePath("/example1/tags/device"),
        SafePath("/example1/tags/directory"),
        SafePath("/example1/tags/file"),
        SafePath("/example1/tags/import1"),
        SafePath("/example1/tags/import2"),
        SafePath("/example1/tags/import3"),
        SafePath("/example1/tags/input"),
        SafePath("/example1/tags/person"),
        SafePath("/example1/tags/programming"),
        SafePath("/example1/tags/software"),
    )

    @Test
    fun testMoveTagDesktop1() {
        val path = SafePath("/example1/tags")
        val oldName = "desktop1"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed")).doesNotExist()
    }

    @Test
    fun testMoveTagImport1() {
        val path = SafePath("/example1/tags")
        val oldName = "import1"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import1.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import1.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagImport2() {
        val path = SafePath("/example1/tags")
        val oldName = "import2"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import2.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import2.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagImport3() {
        val path = SafePath("/example1/tags")
        val oldName = "import3"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import3.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import3.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagCaseSensitive() {
        val path = SafePath("/example1/tags")
        val oldName = "desktop1"
        val newName = "DESKTOP1"
        requestCtx.getInode(path.resolve(oldName)).move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #DESKTOP1")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #DESKTOP1")).doesNotExist()
    }

    @Test
    fun testError1parentLoop() {
        assertThat(requestCtx.getInode(SafePath("/error1parentLoop/tags")).config.errors).containsExactly(
            "/error1parentLoop/.fileadmin.json: Parent loop detected between error1 and error1.",
            "/error1parentLoop/.fileadmin.json: Parent loop detected between error1 and error1.",
            "/error1parentLoop/.fileadmin.json: Parent loop detected between error2 and error1.",
            "/error1parentLoop/.fileadmin.json: 'illegal tag name' is no valid name for a tag.",
            "/error1parentLoop/.fileadmin.json: Parent loop detected between input and input.",
        )
    }

    @Test
    fun testError2import() {
        val inode = requestCtx.getInode(SafePath("/error2import/tags"))
        assertThat(inode.config.filter!!.ctx.registry.tags.keys).contains(
            "import1",
            "import2",
            "import3",
            "import4",
            "import5",
        )
        assertThat(inode.config.errors).containsExactly(
            "/error2import/config/import5.json: Max import level exceeded while importing \"import6.json\"",
        )
    }

    @Test
    fun testExample2() {
        val inode = requestCtx.getInode(SafePath("/example2/tags"))
        assertThat(inode.children).containsExactlyInAnyOrder(
            SafePath("/example2/tags/,evaluate"),
            SafePath("/example2/tags/,operator"),
            SafePath("/example2/tags/!lostAndFound"),
            SafePath("/example2/tags/!unknown"),
            SafePath("/example2/tags/.fileadmin.system"),
            SafePath("/example2/tags/input2"),
            SafePath("/example2/tags/input"),
            SafePath("/example2/tags/tag11"),
            SafePath("/example2/tags/tag12"),
            SafePath("/example2/tags/tag13"),
        )
    }

    @Test
    fun testUnmountInputDirectory() {
        val original = Path.of("$jail/example1/input2")
        val renamed = Path.of("$jail/example1/renamed")
        if (original.exists()) {
            requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
                assertThat(inode.children).containsExactlyInAnyOrderElementsOf(rootFilterNames)
                assertThat(inode.config.errors).isEmpty()
            }
            original.moveTo(renamed)
            Thread.sleep(2)
        }
        requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
            assertThat(inode.children).containsExactlyInAnyOrderElementsOf(rootFilterNames - setOf(SafePath("/example1/tags/input")))
            assertThat(inode.config.errors).containsExactly("Input '/example1/input2' not available.")
        }
        renamed.moveTo(original)
        Thread.sleep(2)
        requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
            assertThat(inode.children).containsExactlyInAnyOrderElementsOf(rootFilterNames)
            assertThat(inode.config.errors).isEmpty()
        }
    }
}