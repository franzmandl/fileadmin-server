package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.PathConditionVersioned
import com.franzmandl.fileadmin.dto.config.SimplePathConditionVersion1
import com.franzmandl.fileadmin.vfs.Inode1
import com.franzmandl.fileadmin.vfs.PathCondition
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest
@ActiveProfiles("test", "jail1")
class ItemContentIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private sealed interface Visited {
        data class OnDirectory(val path: SafePath) : Visited
        data class OnError(val message: String) : Visited
        data class OnFile(val path: SafePath) : Visited
        data class OnName(val path: SafePath) : Visited
    }

    private class Visitor(
        override val condition: PathCondition,
        override val pruneNames: Set<String>,
        private val log: MutableList<Visited>,
    ) : ItemContent.Visitor {
        override val errorHandler = ErrorHandler { log += Visited.OnError(it); null }

        override fun onDirectory(inode: Inode1<*>): Boolean {
            log += Visited.OnDirectory(inode.inode0.path)
            return true
        }

        override fun onFile(inode: Inode1<*>): Boolean {
            log += Visited.OnFile(inode.inode0.path)
            return true
        }

        override fun onName(inode: Inode1<*>): Boolean {
            log += Visited.OnName(inode.inode0.path)
            return true
        }
    }

    private fun find(pathString: String, condition: PathConditionVersioned): List<Visited> {
        val log = LinkedList<Visited>()
        ItemContent.visit(
            requestCtx.createPathFinderCtx(false),
            requestCtx.getInode(SafePath(pathString)),
            Visitor(applicationCtx.mapper.fromVersioned(condition, TestUtil.failErrorHandler, defaultMinDepth = 0), setOf(), log)
        )
        return log
    }

    @Test
    fun testA00() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 0, maxDepth = 0))).isEmpty()
    }

    @Test
    fun testA11() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 1, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA01() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 0, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA02() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 0, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA12() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 1, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA22() {
        assertThat(find("/a", SimplePathConditionVersion1(minDepth = 2, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA00FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 0, maxDepth = 0))).isEmpty()
    }

    @Test
    fun testA11FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 1, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnFile(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA01FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 0, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnFile(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA01FileNameGlobTxt() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*.txt", minDepth = 0, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA02FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 0, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnFile(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnFile(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
            Visited.OnFile(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA12FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 1, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnFile(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnFile(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
            Visited.OnFile(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA22FileNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 2, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnFile(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
            Visited.OnFile(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testErrorFile00FileNameGlobStar() {
        assertThat(find("/errorFile", SimplePathConditionVersion1(fileNameGlob = "*", minDepth = 0, maxDepth = 0))).containsExactly(
            Visited.OnFile(SafePath("/errorFile")),
        )
    }

    @Test
    fun testA00DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 0, maxDepth = 0))).containsExactly(
            Visited.OnDirectory(SafePath("/a")),
        )
    }

    @Test
    fun testA11DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 1, maxDepth = 1))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnDirectory(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnDirectory(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA01DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 0, maxDepth = 1))).containsExactly(
            Visited.OnDirectory(SafePath("/a")),
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnDirectory(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnDirectory(SafePath("/a/errorDirectory")),
        )
    }

    @Test
    fun testA02DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 0, maxDepth = 2))).containsExactly(
            Visited.OnDirectory(SafePath("/a")),
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnDirectory(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnDirectory(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnDirectory(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA12DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 1, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory")),
            Visited.OnDirectory(SafePath("/a/directory")),
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnDirectory(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorFile")),
            Visited.OnName(SafePath("/a/errorDirectory")),
            Visited.OnDirectory(SafePath("/a/errorDirectory")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testA22DirectoryNameGlobStar() {
        assertThat(find("/a", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 2, maxDepth = 2))).containsExactly(
            Visited.OnName(SafePath("/a/directory/aDirectory")),
            Visited.OnDirectory(SafePath("/a/directory/aDirectory")),
            Visited.OnName(SafePath("/a/directory/aFile")),
            Visited.OnName(SafePath("/a/errorDirectory/.gitkeep")),
        )
    }

    @Test
    fun testErrorFile00DirectoryNameGlobStar() {
        assertThat(find("/errorFile", SimplePathConditionVersion1(directoryNameGlob = "*", minDepth = 0, maxDepth = 0))).isEmpty()
    }
}