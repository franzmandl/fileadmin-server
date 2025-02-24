package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TempJailInitializer
import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.nio.file.Path
import kotlin.io.path.writeText

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [TempJailInitializer::class])
@DirtiesContext
class FilterCtxTempJailIT(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.jail.path}") private val jail: Path,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @BeforeEach
    fun beforeEach() {
        TempJailInitializer.beforeEach()
    }

    @Test
    fun testSystemTags() {
        // given
        jail.resolve(applicationCtx.config.fileName).writeText(
            """{
    "_type": "FilterVersion1",
    "output": {
        "_type": "OutputDirectoryVersion1",
        "path": "tags"
    }
}"""
        )
        // when
        val inode = requestCtx.getInode(SafePath("/tags/,evaluate"))
        // then
        assertThat(inode.config.errors).isEmpty()
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(TestUtil.systemTags)
    }

    @Test
    fun testProduct() {
        // given
        jail.resolve(applicationCtx.config.fileName).writeText(
            """{
    "_type": "FilterVersion1",
    "output": {
        "_type": "OutputDirectoryVersion1",
        "path": "tags"
    },
    "relationships": [
        {"_type": "RelationshipDefinitionVersion1", "name": "product", "addSubjectAsParent": true, "roots": ["#product"], "template": {"_type": "TagVersion1", "name": "#product_<>"}},
    null],
    "tags": [
        {"_type": "TagVersion1", "name": "#company", "children": [
            {"_type": "TagVersion1", "name": "#companyA", "children": [
                {"_type": "TagVersion1", "name": "#companyAA", "relationships": {"product": [
                    {"_type": "TagVersion1", "name": "#productAA1"},
                    {"_type": "TagVersion1", "name": "#productAA2"},
                null]}},
            null], "relationships": {"product": [
                {"_type": "TagVersion1", "name": "#productA1"},
                {"_type": "TagVersion1", "name": "#productA2"},
            null]}},
            {"_type": "TagVersion1", "name": "#companyB", "children": [
                {"_type": "TagVersion1", "name": "#companyBB", "relationships": {"product": [
                    {"_type": "TagVersion1", "name": "#productBB1"},
                    {"_type": "TagVersion1", "name": "#productBB2"},
                null]}},
            null], "relationships": {"product": [
                {"_type": "TagVersion1", "name": "#productB1"},
                {"_type": "TagVersion1", "name": "#productB2"},
            null]}},
        null]},
        {"_type": "TagVersion1", "name": "#product"},
    null]
}"""
        )
        // when
        val inode = requestCtx.getInode(SafePath("/tags/,evaluate"))
        // then
        assertThat(inode.config.errors).isEmpty()
        val productCompanyAA = mapOf(
            "productAA1" to null,
            "productAA2" to null,
        )
        val productCompanyA = mapOf(
            "product_companyAA" to productCompanyAA,
            "productA1" to null,
            "productA2" to null,
        )
        val productCompanyBB = mapOf(
            "productBB1" to null,
            "productBB2" to null,
        )
        val productCompanyB = mapOf(
            "product_companyBB" to productCompanyBB,
            "productB1" to null,
            "productB2" to null,
        )
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(
            TestUtil.systemTags + mapOf(
                "product" to mapOf(
                    "product_companyA" to productCompanyA,
                    "product_companyB" to productCompanyB,
                ),
                "company" to mapOf(
                    "companyA" to mapOf(
                        "companyAA" to mapOf(
                            "product_companyAA" to productCompanyAA,
                        ),
                        "product_companyA" to productCompanyA,
                    ),
                    "companyB" to mapOf(
                        "companyBB" to mapOf(
                            "product_companyBB" to productCompanyBB,
                        ),
                        "product_companyB" to productCompanyB,
                    ),
                ),
            ),
        )
    }

    @Test
    fun testResolveName() {
        // given
        jail.resolve(applicationCtx.config.fileName).writeText(
            """{
    "_type": "FilterVersion1",
    "output": {
        "_type": "OutputDirectoryVersion1",
        "path": "tags"
    },
    "tags": [
        {"_type": "TagVersion1", "name": "#company", "children": [
            {"_type": "TagVersion1", "name": "#<parent>A", "children": [
                {"_type": "TagVersion1", "name": "#<parent[0]>A"},
                {"_type": "TagVersion1", "name": "#<isA[0]>1<parent[0]>", "isA": ["#product"]},
                {"_type": "TagVersion1", "name": "#<isA[0,0]>2<parent[  0  ,  0  ]>", "isA": ["#product"]},
            null]},
            {"_type": "TagVersion1", "name": "#<parent>B1#<parent>B2", "children": [
                {"_type": "TagVersion1", "name": "#<parent>A"},
                {"_type": "TagVersion1", "name": "#<parent[1]>B"},
            null]},
        null]},
        {"_type": "TagVersion1", "name": "#product"},
    null]
}"""
        )
        // when
        val inode = requestCtx.getInode(SafePath("/tags/,evaluate"))
        // then
        assertThat(inode.config.errors).containsExactly(
            """/.fileadmin.json: More than one index specified for key "parent" in name "#<isA[0,0]>2<parent[  0  ,  0  ]>" which will be ignored."""
        )
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(
            TestUtil.systemTags + mapOf(
                "company" to mapOf(
                    "companyA" to mapOf(
                        "companyAA" to null,
                        "product1companyA" to null,
                        "product2companyA" to null,
                    ),
                    "companyB1=companyB2" to mapOf(
                        "companyB1A=companyB2A" to null,
                        "companyB2A=companyB1A" to null,
                        "companyB2B" to null,
                    ),
                    "companyB2=companyB1" to mapOf(
                        "companyB1A=companyB2A" to null,
                        "companyB2A=companyB1A" to null,
                        "companyB2B" to null,
                    ),
                ),
                "product" to mapOf(
                    "product1companyA" to null,
                    "product2companyA" to null,
                ),
            )
        )
    }

    @Test
    fun testIllegalName() {
        // given
        jail.resolve(applicationCtx.config.fileName).writeText(
            """{
    "_type": "FilterVersion1",
    "output": {
        "_type": "OutputDirectoryVersion1",
        "path": "tags"
    },
    "tags": [
        {"_type": "TagVersion1", "name": "#illegal tag name"},
    null]
}"""
        )
        // when
        val inode = requestCtx.getInode(SafePath("/tags/,evaluate"))
        // then
        assertThat(inode.config.errors).containsExactly("""/.fileadmin.json: "illegal tag name" is no valid name for a tag.""")
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(TestUtil.systemTags)
    }

    @Test
    fun testPlaceholder() {
        // given
        jail.resolve(applicationCtx.config.fileName).writeText(
            """{
    "_type": "FilterVersion1",
    "output": {
        "_type": "OutputDirectoryVersion1",
        "path": "tags"
    },
    "tags": [
        {"_type": "TagVersion1", "name": "#placeholder1", "placeholder": true},
        {"_type": "TagVersion1", "name": "#placeholder2"},
        {"_type": "TagVersion1", "name": "#placeholder2", "placeholder": true},
        {"_type": "TagVersion1", "name": "#placeholder3", "placeholder": true},
        {"_type": "TagVersion1", "name": "#placeholder3"},
        {"_type": "TagVersion1", "name": "#placeholder4"},
        {"_type": "TagVersion1", "name": "#placeholder4", "placeholder": true},
        {"_type": "TagVersion1", "name": "#placeholder4"},
        {"_type": "TagVersion1", "name": "#placeholder5", "placeholder": true},
        {"_type": "TagVersion1", "name": "#placeholder5"},
        {"_type": "TagVersion1", "name": "#placeholder5", "placeholder": true},
    null]
}"""
        )
        // when
        val inode = requestCtx.getInode(SafePath("/tags/,evaluate"))
        // then
        assertThat(inode.config.errors).containsExactly("""Tag "placeholder1" is still a placeholder and was defined in config files: /.fileadmin.json""")
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(
            TestUtil.systemTags + mapOf("placeholder1" to null, "placeholder2" to null, "placeholder3" to null, "placeholder4" to null, "placeholder5" to null)
        )
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll(@TempDir tempDir: Path) {
            TempJailInitializer.beforeAll(tempDir)
        }
    }
}