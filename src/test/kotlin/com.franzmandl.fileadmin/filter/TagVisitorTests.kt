package com.franzmandl.fileadmin.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class TagVisitorTests {
    private fun getTagNames(value: String, startIndex: Int): List<StringRange<Boolean>> {
        val result = LinkedList<StringRange<Boolean>>()
        TagVisitor.visit(value, startIndex) { result.add(it) }
        return result
    }

    @Test
    fun testVisitTags1() {
        //           012345678901234567890123456789012345678901234567890123456
        val value = "as#df#oiwy_ur#Lfd asf ##öÄü. 342#1a2@r^@@3 #@descendants-"
        assertThat(getTagNames(value, 3)).containsExactly(
            StringRange("oiwy_ur", 6, 12, false),
            StringRange("Lfd", 14, 16, false),
            StringRange("öÄü", 24, 26, false),
            StringRange("1a2", 33, 35, false),
            StringRange("descendants", 45, 55, true),
        )
    }

    @Test
    fun testVisitTags2() {
        //           0123456789
        val value = "#Àéÿ½#1#12"
        assertThat(getTagNames(value, 0)).containsExactly(
            StringRange("Àéÿ", 1, 3, false),
        )
    }
}