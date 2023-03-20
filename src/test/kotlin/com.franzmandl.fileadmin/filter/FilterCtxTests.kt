package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.model.ApplicationCtx
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
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
class FilterCtxTests(
    @Autowired private val applicationCtx: ApplicationCtx,
    @Value("\${application.paths.jail}") private val jail: String,
) {
    private val path = SafePath("/example1/tags")
    private val requestCtx = applicationCtx.createRequestCtx()
    private val filterCtx: FilterCtx

    init {
        val inode = requestCtx.getInode(path)
        assertThat(inode.config.errors).isEmpty()
        inode.children  // Scans for unknown tags.
        filterCtx = inode.config.filter!!.ctx
    }

    private val tagCode = filterCtx.registry.getTag("code")!!
    private val tagMe = filterCtx.registry.getTag("me")!!
    private val tagPerson = filterCtx.registry.getTag("person")!!
    private val tagSoftware = filterCtx.registry.getTag("software")!!
    private val tagUnknown1 = filterCtx.registry.getTag("unknown1")!!

    private val allItemPaths = setOf(
        "/example1/input1/2022/2022-11-22 - #desktop1",
        "/example1/input1/2022/2022-11-22 - content",
        "/example1/input1/2022/2022-11-22 - #@device.txt",
        "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
        "/example1/input1/2022/2022-11-22 - #import1.txt",
        "/example1/input1/2022/2022-11-22 - #import2.txt",
        "/example1/input1/2022/2022-11-22 - #import3.txt",
        "/example1/input1/2022/2022-11-22 - #me.txt",
        "/example1/input1/2022/2022-11-22 - #person.txt",
        "/example1/input1/2022/2022-11-22 - #unknown1#unknown2.txt",
        "/example1/input1/2022/2022-11-22 - #unknown1.txt",
        "/example1/input1/2022/2022-11-22 - #unknown2.txt",
        "/example1/input1/2022/2022-11-22 - content.txt",
        "/example1/input1/2022/2022-11-22 - untagged.txt",
        "/example1/input2/#phone1.js",
    )

    private val rootFilterNames = setOf(
        "!lostAndFound",
        "!unknown",
        "device",
        "directory",
        "file",
        "import1",
        "import2",
        "import3",
        "input",
        "person",
        "programming",
        "software",
    )

    private fun filter(onError: (String) -> Unit, vararg filterStrings: String) =
        filterCtx.filter(requestCtx, SafePath(listOf()), filterStrings.toList(), onError)

    private fun filterItems(vararg filters: Filter) =
        filterCtx.filterItems(requestCtx, filters.toList(), ::onError)

    private fun filterNames(vararg filters: Filter) =
        filterCtx.filterNames(requestCtx, ::onError, FilterCtx.Result(false, filters.toList()))

    private fun onError(message: String) {
        fail<Nothing>("An error occurred: $message")
    }

    private fun createTagFilter(
        tag: Tag,
        reason: TagFilter.Reason = TagFilter.Reason.Any,
        relationship: TagFilter.Relationship = TagFilter.Relationship.Any,
    ): TagFilter =
        TagFilter(tag, reason, relationship)

    private fun getItemPath(item: Item): String =
        item.inode.path.toString()

    @Test
    fun testTrimName() {
        assertThat(FilterFileSystem.trimName("_")).isEqualTo("")
        assertThat(FilterFileSystem.trimName("_unknown")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("_unknown=something")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("unknown=something")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("unknown +")).isEqualTo("unknown")
    }

    @Test
    fun testGetItems() {
        assertThat(filterItems().map(::getItemPath)).containsExactlyInAnyOrderElementsOf(allItemPaths)
    }

    @Test
    fun testGetItemsNotTagMe() {
        assertThat(filterItems(NotFilter(createTagFilter(tagMe))).map(::getItemPath)).containsExactlyInAnyOrderElementsOf(
            allItemPaths - setOf(
                "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
                "/example1/input1/2022/2022-11-22 - #me.txt",
            )
        )
    }

    @Test
    fun testMoveItem() {
        val original = Path.of("$jail/example1/input1/2022/2022-11-22 - #unknown1.txt")
        val renamed = Path.of("$jail/example1/input1/2022/2022-11-22 - #orphan.txt")
        if (original.exists()) {
            assertThat(filterItems().map(::getItemPath)).containsExactlyInAnyOrderElementsOf(allItemPaths)
            original.moveTo(renamed)
            Thread.sleep(2)
        }
        assertThat(filterItems().map(::getItemPath)).containsExactlyInAnyOrderElementsOf(
            allItemPaths - setOf(
                "/example1/input1/2022/2022-11-22 - #unknown1.txt"
            ) + setOf(
                "/example1/input1/2022/2022-11-22 - #orphan.txt"
            )
        )
        renamed.moveTo(original)
        Thread.sleep(2)
        assertThat(filterItems().map(::getItemPath)).containsExactlyInAnyOrderElementsOf(allItemPaths)
    }

    @Test
    fun testPruneNamesGetItems() {
        assertThat(filterItems(createTagFilter(filterCtx.registry.tagLostAndFound)).map(::getItemPath)).containsExactlyInAnyOrder(
            "/example1/input1/2022/2022-11-22 - untagged.txt",
        )
    }

    @Test
    fun testFilterItemsByTag() {
        assertThat(filterItems(createTagFilter(tagUnknown1)).map(::getItemPath)).containsExactlyInAnyOrder(
            "/example1/input1/2022/2022-11-22 - #unknown1#unknown2.txt",
            "/example1/input1/2022/2022-11-22 - #unknown1.txt",
        )
    }

    @Test
    fun testFilterItemsByTwin() {
        assertThat(filterItems(createTagFilter(tagMe)).map(::getItemPath)).containsExactlyInAnyOrder(
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
            "'family",
            "'FranzMandl=me",
            "'KeaganValencia",
            "'LandenKirk",
            "'me=FranzMandl",
        )
    }

    @Test
    fun testFilterTagsByNotTagPerson() {
        assertThat(filterNames(NotFilter(createTagFilter(tagPerson)))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("person")
        )
    }

    @Test
    fun testFilterTagsByTagUnknown() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.tagUnknown))).containsExactlyInAnyOrder(
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
        assertThat(filterNames(createTagFilter(filterCtx.registry.tagUnknown), createTagFilter(tagUnknown1))).containsExactlyInAnyOrder(
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
        assertThat(filterNames(createTagFilter(filterCtx.registry.tagFile))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("directory", "file", "programming", "software")
        )
    }

    @Test
    fun testFilterTagsByTagInput() {
        assertThat(filterNames(createTagFilter(filterCtx.registry.tagInput))).containsExactlyInAnyOrderElementsOf(
            rootFilterNames - setOf("input") + setOf("'input1", "'input2")
        )
    }

    @Test
    fun testNotOperator() {
        val errors = mutableListOf<String>()
        val expected = (rootFilterNames - setOf("person") + setOf(",else", ",evaluate", ",operator")).map { SafePath(listOf(it)) }
        assertThat(filter(errors::add, ",operator", "not", "person").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",operator,not", "person").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",not", "person").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
    }

    @Test
    fun testIllegalOperator() {
        val errors = mutableListOf<String>()
        filter(errors::add, ",operator", "illegalOperator1", "person")
        filter(errors::add, ",operator,illegalOperator2", "person")
        filter(errors::add, ",illegalOperator3", "person")
        assertThat(errors).containsExactly(
            "Illegal operator: illegalOperator1",
            "Illegal operator: illegalOperator2",
            "Illegal operator: illegalOperator3",
        )
    }

    @Test
    fun testNotOperatorEvaluate() {
        val errors = mutableListOf<String>()
        val expected = (allItemPaths - setOf(
            "/example1/input1/2022/2022-11-22 - #FranzMandl.txt",
            "/example1/input1/2022/2022-11-22 - #me.txt",
            "/example1/input1/2022/2022-11-22 - #person.txt",
            "/example1/input1/2022/2022-11-22 - content.txt",
        )).map { SafePath(it) }
        assertThat(filter(errors::add, ",operator", "not", "person", ",evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",operator", "not", "person,evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",operator,not", "person", ",evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",operator,not", "person,evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",not", "person", ",evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
        assertThat(filter(errors::add, ",not", "person,evaluate").children).containsExactlyInAnyOrderElementsOf(expected)
        assertThat(errors).isEmpty()
    }

    @Test
    fun testIllegalEvaluateAppendix() {
        val errors = mutableListOf<String>()
        filter(errors::add, ",operator", "evaluate", "appendix1")
        filter(errors::add, ",operator", "evaluate,operator", "appendix1")
        filter(errors::add, ",operator,evaluate", "appendix2")
        filter(errors::add, ",operator,evaluate,operator", "appendix2")
        filter(errors::add, ",evaluate", "appendix3")
        filter(errors::add, ",evaluate,operator", "appendix3")
        assertThat(errors).containsExactly(
            "Illegal appendix: /appendix1",
            "Illegal appendix: ,operator/appendix1",
            "Illegal appendix: /appendix2",
            "Illegal appendix: ,operator/appendix2",
            "Illegal appendix: /appendix3",
            "Illegal appendix: ,operator/appendix3",
        )
    }
}