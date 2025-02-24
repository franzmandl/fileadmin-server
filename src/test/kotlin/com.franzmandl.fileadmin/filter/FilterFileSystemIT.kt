package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.TestUtil
import com.franzmandl.fileadmin.TestUtil.times
import com.franzmandl.fileadmin.common.CommonUtil
import com.franzmandl.fileadmin.common.ErrorHandler
import com.franzmandl.fileadmin.dto.ApplicationCtx
import com.franzmandl.fileadmin.dto.config.CommandId
import com.franzmandl.fileadmin.vfs.SafePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test", "jail4")
class FilterFileSystemIT(
    @Autowired private val applicationCtx: ApplicationCtx,
) {
    private val requestCtx = applicationCtx.createRequestCtx(null)

    @Test
    fun testError1parentLoop() {
        assertThat(requestCtx.getInode(SafePath("/error1parentLoop/tags")).config.errors).containsExactly(
            """/error1parentLoop/.fileadmin.json: Parent loop detected between error1 and error1.""",
            """/error1parentLoop/.fileadmin.json: Parent loop detected between error1 and error1.""",
            """/error1parentLoop/.fileadmin.json: Parent loop detected between error2 and error1.""",
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
            """/error2import/config/import5.json: Max import level exceeded while importing "import6.json".""",
        )
    }

    @Test
    fun testExample2() {
        val inode = requestCtx.getInode(SafePath("/example2/tags"))
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example2/tags/,evaluate"),
            SafePath("/example2/tags/,operator"),
            SafePath("/example2/tags/!lostAndFound"),
            SafePath("/example2/tags/!unknown"),
            SafePath("/example2/tags/.fileadmin.system"),
            SafePath("/example2/tags/emptyName"),
            SafePath("/example2/tags/input"),
            SafePath("/example2/tags/tag1"),
        )
    }

    @Test
    fun testExample4() {
        val inode = requestCtx.getInode(SafePath("/example4/tags/,evaluate"))
        assertThat(inode.config.errors).containsExactlyElementsOf(listOf("""Path "/example4/inputFile2" is not a directory.""") * 2)
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example4/inputDirectory1"),
            SafePath("/example4/inputFile1"),
            SafePath("/example4/tags/,evaluate/.fileadmin.system"),
        )
    }

    @Test
    fun testExample3() {
        val inode = requestCtx.getInode(SafePath("/example3/tags/,evaluate"))
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example3/tags/,evaluate/.fileadmin.system"),
            SafePath("/example3/input1/2001/2001-01/2001-01-01 - item.txt"),
            SafePath("/example3/input1/2001/2001-01/item.txt"),
            SafePath("/example3/input1/2001/2001-02 - suffix/2001-02-02 - item.txt"),
            SafePath("/example3/input1/2001/2001-00-00 - item.txt"),
            SafePath("/example3/input1/2001/item.txt"),
            SafePath("/example3/input1/2002 - suffix/2002-02/2002-02-02 - item.txt"),
            SafePath("/example3/input1/2002 - suffix/2002-02/item.txt"),
            SafePath("/example3/input1/2002 - suffix/2002-00-00 - item.txt"),
            SafePath("/example3/input1/2002 - suffix/item.txt"),
            SafePath("/example3/input1/2010/2015-05/2010-01-01 - item.txt"),
            SafePath("/example3/input1/2010/2015-05/2010-05-01 - item.txt"),
            SafePath("/example3/input1/2010/2015-05/2015-01-01 - item.txt"),
            SafePath("/example3/input1/2010/2015-05/2015-05-01 - item.txt"),
            SafePath("/example3/input1/2010/2010"),
            SafePath("/example3/input1/2010/2015"),
            SafePath("/example3/input1/2010/2015-05/2015-05"),
            SafePath("/example3/input1/0000 - item.txt"),
            SafePath("/example3/input1/0000-00 - item.txt"),
            SafePath("/example3/input1/0000-00-00 - item.txt"),
            SafePath("/example3/input1/item.txt"),
            SafePath("/example3/input2/level1/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01-01 - item.txt"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item"),
            SafePath("/example3/input2/level1/2001/2001-01/2001-01-01 - item.txt"),
        )
        assertThat(inode.config.errors).containsExactlyElementsOf(
            listOf(
                """Path "/example3/input1/item.txt" does not have a time.""",
                """Path "/example3/input1/2010/2010" must at least specify a month.""",
                """Path "/example3/input1/2010/2015" does not belong into year 2010.""",
                """Path "/example3/input1/2010/2015" must at least specify a month.""",
                """Path "/example3/input1/2010/2015-05" does not belong into year 2010.""",
                """Path "/example3/input1/2010/2015-05/2015-05" must specify a day.""",
                """Path "/example3/input1/2010/2015-05/2010-01-01 - item.txt" does not belong into year 2015.""",
                """Path "/example3/input1/2010/2015-05/2010-05-01 - item.txt" does not belong into year 2015.""",
                """Path "/example3/input1/2010/2015-05/2015-01-01 - item.txt" does not belong into month 05.""",
                """Path "/example3/input1/0000-00 - item.txt" must specify a day.""",
                """Path "/example3/input1/2001/item.txt" does not have a time.""",
                """Path "/example3/input1/2001/2001-01/item.txt" does not have a time.""",
                """Path "/example3/input1/0000 - item.txt" must specify a day.""",
                """Path "/example3/input1/2002 - suffix/item.txt" does not have a time.""",
                """Path "/example3/input1/2002 - suffix/2002-02/item.txt" does not have a time.""",
            ) * 2
        )
        inode.config.filter!!.ctx.getLockedItemsList(requestCtx, CommandId.Add, ErrorHandler.noop).forEach { item ->
            assertThat(item.time).`as`(item.inode.inode0.path.absoluteString).isEqualTo(
                when (item.inode.inode0.path) {
                    SafePath("/example3/input1/2001/item.txt") -> LocalDate.of(2001, 1, 1)
                    SafePath("/example3/input1/2001/2001-01/item.txt") -> LocalDate.of(2001, 1, 1)
                    SafePath("/example3/input1/2002 - suffix/item.txt") -> LocalDate.of(2002, 1, 1)
                    SafePath("/example3/input1/2002 - suffix/2002-02/item.txt") -> LocalDate.of(2002, 2, 1)
                    else -> CommonUtil.parseDate(item.inode.inode0.path.name)
                }
            )
        }
    }

    @Test
    fun testExample5Items() {
        val inode = requestCtx.getInode(SafePath("/example5/tags/,evaluate"))
        assertThat(inode.config.errors).containsExactlyInAnyOrder(
            """Tag "TagI" is still a placeholder and was defined in config files: """,
            """Tag "TagH_Input5mPrefix" is still a placeholder and was defined in config files: """,
        )
        assertThat(inode.config.filter!!.ctx.registry.systemTags.unknown.getSequenceOfChildren(Tag.ChildrenParameter.all).map { it.name }.toList()).isEmpty()
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example5/tags/,evaluate/.fileadmin.system"),
            SafePath("/example5/input1/2001/2001-01-01 - item"),
            SafePath("/example5/input1/2001/2001-01-01 - item/item/item.txt"),
            SafePath("/example5/input1/2001/2001-01-01 - item/item.txt"),
            SafePath("/example5/input1/2001/2001-00-00 - item.txt"),
            SafePath("/example5/input2/A/Abc (2000)"),
            SafePath("/example5/input2/A/Abcd (2001)"),
            SafePath("/example5/input2/X/Xy (2002)"),
            SafePath("/example5/input2/X/Xyz (2003)"),
            SafePath("/example5/input3/1999 or before/A.b.c.txt"),
            SafePath("/example5/input3/1999 or before/Abc.txt"),
            SafePath("/example5/input3/1999 or before/B. c. d.txt"),
            SafePath("/example5/input3/1999 or before/Bcd.txt"),
            SafePath("/example5/input3/2000-2009/Cde #tag.txt"),
            SafePath("/example5/input3/2000-2009/Def.txt"),
            SafePath("/example5/input3/2010/Efg.txt"),
            SafePath("/example5/input3/2010/Fgh.txt"),
            SafePath("/example5/input3/Ghi/Hij #tag/Ijk.txt"),
            SafePath("/example5/input3/Ghi/Hij #tag/Jkl.txt"),
            SafePath("/example5/input3/Ghi/Klm.txt"),
            SafePath("/example5/input3/Ghi/Lmn.txt"),
            SafePath("/example5/input4/Abc/A1/B1.txt"),
            SafePath("/example5/input4/Abc/A1/B2.txt"),
            SafePath("/example5/input4/Abc/A2/B1.txt"),
            SafePath("/example5/input4/Abc/A2/B2.txt"),
            SafePath("/example5/input4/Def/A1/B1.txt"),
            SafePath("/example5/input4/Def/A1/B2.txt"),
            SafePath("/example5/input4/Def/A2/B1.txt"),
            SafePath("/example5/input4/Def/A2/B2.txt"),
            SafePath("/example5/input5/Ghi/2000-01-01 - item.txt"),
            SafePath("/example5/input5/Jkl/2000-01-02 - item.txt"),
            SafePath("/example5/input5m/2000-01-01 - #TagA item.txt"),
            SafePath("/example5/input5m/2000-01-01 - #TagA#TagB item.txt"),
            SafePath("/example5/input5m/2000-01-01 - item #TagC_Input5mPrefix.txt"),
            SafePath("/example5/input5m/2000-01-01 - item #TagD_ef_Input5mPrefix.txt"),
            SafePath("/example5/input5m/2000-01-01 - item #TagE #TagF_Input5mPrefix.txt"),
            SafePath("/example5/input5m/2000-01-01 - item #TagG#TagH_Input5mPrefix.txt"),
            SafePath("/example5/input5m/2000-01-01 - item #TagI.txt"),
            SafePath("/example5/input5m/2000-01-01 - item.txt"),
        )
    }

    @Test
    fun testExample5Tags() {
        val inode = requestCtx.getInode(SafePath("/example5/tags/,evaluate"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(TestUtil.getTagHierarchy(inode.config.filter!!.ctx.registry.tags.values)).isEqualTo(
            TestUtil.systemTags + mapOf(
                "import1" to null,
                "input" to mapOf(
                    "input1_input" to null,
                    "20010000_item" to null,
                    "20010101_item" to mapOf(
                        "20010101_itemitem" to mapOf(
                            "20010101_itemitemitem" to null,
                        ),
                    ),
                    "input2_input" to null,
                    "PrefixA" to mapOf(
                        "Abc_Year2000" to null,
                        "Abcd_Year2001" to null,
                    ),
                    "PrefixX" to mapOf(
                        "Xy_Year2002" to null,
                        "Xyz_Year2003" to null,
                    ),
                    "input3_input" to null,
                    "Prefix1999OrBefore" to mapOf(
                        "Abc" to null,
                        "ABC" to null,
                        "Bcd" to null,
                        "BCD" to null,
                    ),
                    "Prefix2010" to mapOf(
                        "Efg" to null,
                        "Fgh" to null,
                    ),
                    "Prefix20002009" to mapOf(
                        "Cde_tag" to null,
                        "Def" to null,
                    ),
                    "PrefixGhi" to mapOf(
                        "Hij_tag" to mapOf(
                            "Ijk" to null,
                            "Jkl" to null,
                        ),
                        "Klm" to null,
                        "Lmn" to null,
                    ),
                    "input4_input" to null,
                    "Abc_Suffix" to mapOf(
                        "Abc_A1" to mapOf(
                            "Abc_A1B1" to null,
                            "Abc_A1B2" to null,
                        ),
                        "Abc_A2" to mapOf(
                            "Abc_A2B1" to null,
                            "Abc_A2B2" to null,
                        ),
                    ),
                    "Def_Suffix" to mapOf(
                        "Def_A1" to mapOf(
                            "Def_A1B1" to null,
                            "Def_A1B2" to null,
                        ),
                        "Def_A2" to mapOf(
                            "Def_A2B1" to null,
                            "Def_A2B2" to null,
                        ),
                    ),
                    "input5_input" to null,
                    "Ghi_Suffix" to mapOf(
                        "Ghi_20000101_item" to null,
                    ),
                    "Jkl_Suffix" to mapOf(
                        "Jkl_20000102_item" to null,
                    ),
                    "input5m_input" to null,
                    "input20000101_TagAItem" to null,
                    "input20000101_TagATagBItem" to null,
                    "TagC_Input5mPrefix" to mapOf(
                        "TagC_20000101_item" to null,
                    ),
                    "TagD_ef_Input5mPrefix" to mapOf(
                        "TagD_ef_20000101_item" to null,
                    ),
                    "TagF_Input5mPrefix" to mapOf(
                        "TagF_20000101_item_TagE" to null,
                    ),
                    "TagH_Input5mPrefix" to mapOf(
                        "TagH_20000101_item_TagG" to null,
                    ),
                    "TagI" to mapOf(
                        "TagI_20000101_item" to null,
                    ),
                    "input20000101_item" to null,
                    "input6_input" to null,
                ),
                "TagA" to null,
                "TagB" to null,
                "TagE" to null,
                "TagG" to null,
            ),
        )
    }

    @Test
    fun testExample5InodeTag() {
        val inode = requestCtx.getInode(SafePath("/example5/tags/input1_input"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example5/tags/input1_input/.fileadmin.system"),
            SafePath("/example5/tags/input1_input/,else"),
            SafePath("/example5/tags/input1_input/,evaluate"),
            SafePath("/example5/tags/input1_input/,operator"),
            SafePath("/example5/tags/input1_input/20010000_item"),
            SafePath("/example5/tags/input1_input/20010101_item"),
            SafePath("/example5/tags/input1_input/directory"),
            SafePath("/example5/tags/input1_input/file"),
        )
    }

    @Test
    fun testExample5InodeTagItem() {
        requestCtx.getInode(SafePath("/example5/tags")) // Triggers item scan.
        val inode = requestCtx.getInode(SafePath("/example5/tags/input1_input/20010101_item"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example5/tags/input1_input/20010101_item/.fileadmin.system"),
            SafePath("/example5/tags/input1_input/20010101_item/,else"),
            SafePath("/example5/tags/input1_input/20010101_item/,evaluate"),
            SafePath("/example5/tags/input1_input/20010101_item/,operator"),
            SafePath("/example5/tags/input1_input/20010101_item/'20010101_itemitem"),
            SafePath("/example5/tags/input1_input/20010101_item/directory"),
            SafePath("/example5/tags/input1_input/20010101_item/file"),
        )
    }

    @Test
    fun testExample5InodeTagItemItem() {
        requestCtx.getInode(SafePath("/example5/tags")) // Triggers item scan.
        val inode = requestCtx.getInode(SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem"))
        assertThat(inode.config.errors).isEmpty()
        assertThat(inode.inode0.children).containsExactlyInAnyOrder(
            SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem/.fileadmin.system"),
            SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem/,else"),
            SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem/,evaluate"),
            SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem/,operator"),
            SafePath("/example5/tags/input1_input/20010101_item/20010101_itemitem/'20010101_itemitemitem"),
        )
    }
}