package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.dto.ApplicationCtx
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
class FilterFileSystemChangeIT(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.jail.path}") private val jail: String,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private val rootFilterNames = setOf(
        SafePath("/example1/tags/,evaluate"),
        SafePath("/example1/tags/,operator"),
        SafePath("/example1/tags/!lostAndFound"),
        SafePath("/example1/tags/!unknown"),
        SafePath("/example1/tags/.fileadmin.system"),
        SafePath("/example1/tags/company"),
        SafePath("/example1/tags/device"),
        SafePath("/example1/tags/directory"),
        SafePath("/example1/tags/file"),
        SafePath("/example1/tags/emptyContent"),
        SafePath("/example1/tags/emptyName"),
        SafePath("/example1/tags/import1"),
        SafePath("/example1/tags/import2"),
        SafePath("/example1/tags/import3"),
        SafePath("/example1/tags/input"),
        SafePath("/example1/tags/legacy"),
        SafePath("/example1/tags/person"),
        SafePath("/example1/tags/preposition"),
        SafePath("/example1/tags/programming"),
        SafePath("/example1/tags/prune"),
        SafePath("/example1/tags/software"),
        SafePath("/example1/tags/TaskStatus"),
    )

    @Test
    fun testMoveTagDesktop1() {
        val path = SafePath("/example1/tags")
        val oldName = "desktop1"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed")).doesNotExist()
    }

    @Test
    fun testMoveTagImport1() {
        val path = SafePath("/example1/tags")
        val oldName = "import1"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import1.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import1.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagImport2() {
        val path = SafePath("/example1/tags")
        val oldName = "import2"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import2.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import2.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagImport3() {
        val path = SafePath("/example1/tags")
        val oldName = "import3"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import3.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #import3.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #renamed.txt")).doesNotExist()
    }

    @Test
    fun testMoveTagCaseSensitive() {
        val path = SafePath("/example1/tags")
        val oldName = "desktop1"
        val newName = "DESKTOP1"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #DESKTOP1")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #DESKTOP1")).doesNotExist()
    }

    @Test
    fun testMoveTagInContent() {
        val path = SafePath("/example1/tags")
        val oldName = "printer1"
        val newName = "renamed"
        requestCtx.getInode(path.resolve(oldName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(newName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1/#printer1.txt")).doesNotExist()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1/#renamed.txt")).exists()
        Thread.sleep(2)
        requestCtx.getInode(path.resolve(newName)).inode0.move(requestCtx, requestCtx.getInode(path.resolve(oldName)))
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1/#printer1.txt")).exists()
        assertThat(Path.of("$jail/example1/input1/2022/2022-11-22 - #desktop1/#renamed.txt")).doesNotExist()
    }

    @Test
    fun testUnmountInputDirectory() {
        val original = Path.of("$jail/example1/input2")
        val renamed = Path.of("$jail/example1/renamed")
        if (original.exists()) {
            requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
                assertThat(inode.inode0.children).containsExactlyInAnyOrderElementsOf(rootFilterNames)
                assertThat(inode.config.errors).isEmpty()
            }
            original.moveTo(renamed)
            Thread.sleep(2)
        }
        requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
            assertThat(inode.inode0.children).containsExactlyInAnyOrderElementsOf(rootFilterNames)
            assertThat(inode.config.errors).containsExactly("""Input "/example1/input2" not available.""")
        }
        renamed.moveTo(original)
        Thread.sleep(2)
        requestCtx.getInode(SafePath("/example1/tags")).let { inode ->
            assertThat(inode.inode0.children).containsExactlyInAnyOrderElementsOf(rootFilterNames)
            assertThat(inode.config.errors).isEmpty()
        }
    }
}