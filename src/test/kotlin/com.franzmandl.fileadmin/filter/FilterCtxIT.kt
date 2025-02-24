package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FilterCtxIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val path = SafePath("/example1/tags")
    private val requestCtx = applicationCtx.createRequestCtx(null)
    private final val filterCtx: FilterCtx

    init {
        val inode = requestCtx.getInode(path)
        assertThat(inode.config.errors).isEmpty()
        filterCtx = inode.config.filter!!.ctx
    }

    private val tagCode = filterCtx.registry.getTag("code")!!
    private val tagMe = filterCtx.registry.getTag("me")!!
    private val tagPerson = filterCtx.registry.getTag("person")!!
    private val tagSoftware = filterCtx.registry.getTag("software")!!
    private val tagUnknown1 = filterCtx.registry.getTag("unknown1")!!

    private val rootFilterNames = setOf(
        "!lostAndFound",
        "!unknown",
        "company",
        "device",
        "directory",
        "emptyContent",
        "emptyName",
        "file",
        "import1",
        "import2",
        "import3",
        "input",
        "legacy",
        "person",
        "preposition",
        "programming",
        "prune",
        "software",
        "TaskStatus",
    )

    private fun filter(errorHandler: ErrorHandler, vararg filterStrings: String) =
        filterCtx.filter(requestCtx, SafePath(listOf()), filterStrings.toList(), errorHandler)

    private fun filterItems(vararg filters: Filter) =
        filterCtx.filterItems(
            requestCtx,
            filterCtx.getLockedItemsList(requestCtx, CommandId.Add, TestUtil.failErrorHandler).toMutableList(),
            filters.toList(),
            TestUtil.failErrorHandler,
        )

    private fun filterNames(vararg filters: Filter): MutableSet<String> {
        val filterList = filters.toList()
        return filterCtx.filterNames(
            filterCtx.filterItems(
                requestCtx,
                filterCtx.getLockedItemsList(requestCtx, CommandId.Add, TestUtil.failErrorHandler).toMutableList(),
                filterList,
                TestUtil.failErrorHandler,
            ),
            filterList
        ).names
    }

    private fun createTagFilter(
        tag: Tag,
        reason: TagFilter.Reason = TagFilter.Reason.Any,
        relationship: TagFilter.Relationship = TagFilter.Relationship.Any,
    ): TagFilter =
        TagFilter(tag, reason, relationship)

    @Test
    fun testGetItems() {
        assertThat(filterItems().map(TestUtil::getItemPath)).containsExactlyInAnyOrderElementsOf(TestUtil.jail4Example1ItemPaths)
    }

    @Test
    fun testGetItemsNotTagMe() {
        assertThat(filterItems(NotFilter(createTagFilter(tagMe))).map(TestUtil::getItemPath)).containsExactlyInAnyOrderElementsOf(
            TestUtil.jail4Example1ItemPaths - setOf(
                "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
                "/example1/input1/2022/2022-11-22 - #me.txt",
            )
        )
    }

    @Test
    fun testPruneNamesGetItems() {
        assertThat(filterItems(createTagFilter(filterCtx.registry.systemTags.lostAndFound)).map(TestUtil::getItemPath)).containsExactlyInAnyOrder(
            "/example1/input1/2022/2022-11-22 - untagged.txt",
        )
    }

    @Test
    fun testFilterItemsByTag() {
        assertThat(filterItems(createTagFilter(tagUnknown1)).map(TestUtil::getItemPath)).containsExactlyInAnyOrder(
            "/example1/input1/2022/2022-11-22 - #unknown1#unknown2.txt",
            "/example1/input1/2022/2022-11-22 - #unknown1.txt",
        )
    }

    @Test
    fun testFilterItemsByTwin() {
        assertThat(filterItems(createTagFilter(tagMe)).map(TestUtil::getItemPath)).containsExactlyInAnyOrder(
            "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
            "/example1/input1/2022/2022-11-22 - #me.txt",
        )
    }

    @Test
    fun testFilterRootTags() {
        assertThat(filterNames()).containsExactlyInAnyOrderElementsOf(rootFilterNames)
    }

    @Test
    fun testFilterTagsByTagMe() {
        assertThat(filterNames(createTagFilter(tagMe))).containsExactlyInAnyOrder()
    }

    @Test
    fun testFilterTagsByTagPerson() {
        assertThat(filterNames(createTagFilter(tagPerson))).containsExactlyInAnyOrder(
            "'Dad",
            "'FranzMandl=me",
            "'group_CompanyA",
            "'KeaganValencia",
            "'LandenKirk",
            "'me=FranzMandl",
            "'parents",
            "company",
            "device",
            "EmployeeA1",
            "EmployeeA2",
            "emptyContent",
            "emptyName",
            "preposition",
        )
    }

    @Test
    fun test1() {
        val hierarchy = TestUtil.getTagHierarchy(tagSoftware)!!
        assertThat(hierarchy.toKotlinCode()).isEqualTo(
            """mapOf("code" to mapOf("js=javascript" to null,
"javascript=js" to null,
),
),
"""
        )
        assertThat(hierarchy).isEqualTo(
            mapOf(
                "code" to mapOf(
                    "js=javascript" to null,
                    "javascript=js" to null,
                ),
            ),
        )
    }

    @Test
    fun testFilterTagsByNotTagPerson() {
        assertThat(filterNames(NotFilter(createTagFilter(tagPerson)))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("company", "person", "preposition")
        )
    }

    @Test
    fun testFilterTagsByTagUnknown() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.systemTags.unknown))).containsExactlyInAnyOrder(
            "'unknown1",
            "'unknown2",
        )
    }

    @Test
    fun testFilterTagsByTagUnknown1() {
        assertThat(filterNames(createTagFilter(tagUnknown1))).containsExactlyInAnyOrder(
            "unknown2",
        )
    }

    @Test
    fun testFilterTagsByTagUnknownUnknown1() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.systemTags.unknown), createTagFilter(tagUnknown1))).containsExactlyInAnyOrder(
            "unknown2",
        )
    }

    @Test
    fun testFilterTagsByTagCode() {
        assertThat(filterNames(createTagFilter(tagCode))).containsExactlyInAnyOrder()
    }

    @Test
    fun testFilterTagsByTagSoftware() {
        assertThat(filterNames(createTagFilter(tagSoftware))).containsExactlyInAnyOrder()
    }

    @Test
    fun testFilterTagsByTagFile() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.systemTags.file))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("directory", "file", "programming", "software")
        )
    }

    @Test
    fun testFilterTagsByTagInput() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.systemTags.input))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("input") + setOf("'input1_input", "'input2_input", "'input3_input")
        )
    }

    @Test
    fun testNotOperator() {
        val errorHandler = TestUtil.ListErrorHandler()
        val expected = (rootFilterNames - setOf("company", "person", "preposition") + setOf(",else", ",evaluate", ",operator", ".fileadmin.system")).map { SafePath(listOf(it)) }
        assertThat(filter(errorHandler, ",operator", "not", "person").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",operator,not", "person").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",not", "person").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
    }

    @Test
    fun testIllegalOperator() {
        val errorHandler = TestUtil.ListErrorHandler()
        filter(errorHandler, ",operator", "illegalOperator1", "person")
        filter(errorHandler, ",operator,illegalOperator2", "person")
        filter(errorHandler, ",illegalOperator3", "person")
        assertThat(errorHandler.errors).containsExactly(
            "Illegal operator: illegalOperator1",
            "Illegal operator: illegalOperator2",
            "Illegal operator: illegalOperator3",
        )
    }

    @Test
    fun testNotOperatorEvaluate() {
        val errorHandler = TestUtil.ListErrorHandler()
        val expected = (TestUtil.jail4Example1ItemPaths - setOf(
            "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
            "/example1/input1/2022/2022-11-22 - #me.txt",
            "/example1/input1/2022/2022-11-22 - #person.txt",
            "/example1/input1/2022/2022-11-22 - content1.txt",
            "/example1/input1/2022/2022-11-22 - content2.txt",
        ) + setOf("/.fileadmin.system")).map { SafePath(it) }
        assertThat(filter(errorHandler, ",operator", "not", "person", ",evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",operator", "not", "person,evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",operator,not", "person", ",evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",operator,not", "person,evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",not", "person", ",evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
        assertThat(filter(errorHandler, ",not", "person,evaluate").childSet.children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errorHandler.errors).isEmpty()
    }
}