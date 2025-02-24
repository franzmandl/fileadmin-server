package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.dto.ApplicationCtx
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail1")
class GlobFileSystemIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    private fun getList(string: String) =
        requestCtx.createPathFinderCtx(false).createPathFinder(SafePath(string)).find().inode0.children

    @Test
    fun testWildcardDirectory() {
        assertThat(getList("/*/directory")).containsExactlyInAnyOrder(
            SafePath("/a/directory/aDirectory"),
            SafePath("/a/directory/aFile"),
            SafePath("/b/directory/bDirectory"),
            SafePath("/b/directory/bFile"),
            SafePath("/c/directory/cDirectory"),
            SafePath("/c/directory/cFile"),
        )
    }

    @Test
    fun testWildcard() {
        assertThat(getList("/*")).containsExactlyInAnyOrder(
            SafePath("/a/directory"),
            SafePath("/a/errorDirectory"),
            SafePath("/a/errorFile"),
            SafePath("/b/directory"),
            SafePath("/b/errorDirectory"),
            SafePath("/b/errorFile"),
            SafePath("/c/directory"),
            SafePath("/c/errorDirectory"),
            SafePath("/c/errorFile"),
            SafePath("/errorDirectory/.gitkeep"),
        )
    }

    @Test
    fun testWildcardWildcard() {
        assertThat(getList("/*/*")).containsExactlyInAnyOrder(
            SafePath("/a/directory/aFile"),
            SafePath("/a/directory/aDirectory"),
            SafePath("/a/errorDirectory/.gitkeep"),
            SafePath("/b/directory/bDirectory"),
            SafePath("/b/directory/bFile"),
            SafePath("/b/errorDirectory/.gitkeep"),
            SafePath("/c/directory/cDirectory"),
            SafePath("/c/directory/cFile"),
            SafePath("/c/errorDirectory/.gitkeep"),
        )
    }

    @Test
    fun testWildcardDirectoryWildcard() {
        assertThat(getList("/*/directory/*")).containsExactlyInAnyOrder(
            SafePath("/a/directory/aDirectory/errorDirectory"),
            SafePath("/a/directory/aDirectory/errorFile"),
            SafePath("/b/directory/bDirectory/errorDirectory"),
            SafePath("/b/directory/bDirectory/errorFile"),
            SafePath("/c/directory/cDirectory/errorDirectory"),
            SafePath("/c/directory/cDirectory/errorFile"),
        )
    }
}