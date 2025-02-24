package com.franzmandl.fileadmin.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class FilterFileSystemTest {
    @Test
    fun testTrimName() {
        assertThat(FilterFileSystem.trimName("_")).isEqualTo("")
        assertThat(FilterFileSystem.trimName("_unknown")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("_unknown=something")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("unknown=something")).isEqualTo("unknown")
        assertThat(FilterFileSystem.trimName("unknown +")).isEqualTo("unknown")
    }

    @Test
    fun testVisitTags1() {
        //           012345678901234567890123456789012345678901234567890123456
        val value = "as#df#oiwy_ur#Lfd asf ##öÄü. 342#1a2@r^@@3 #descendants-"
        assertThat(FilterFileSystem.getSequenceOfConsecutiveTagNames(value, 3).toList()).containsExactly(
            listOf(StringRange("oiwy_ur", 6, 12, null), StringRange("Lfd", 14, 16, null)),
            listOf(StringRange("öÄü", 24, 26, null)),
            listOf(StringRange("1a2", 33, 35, null)),
            listOf(StringRange("descendants", 44, 54, null)),
        )
    }

    @Test
    fun testVisitTags2() {
        //           0123456789
        val value = "#Àéÿ½#1#12"
        assertThat(FilterFileSystem.getSequenceOfConsecutiveTagNames(value, 0).toList()).containsExactly(
            listOf(StringRange("Àéÿ", 1, 3, null)),
        )
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testToTagName(part: String, expected: String) {
        assertThat(FilterFileSystem.toTagName(part)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun testToTagName(): Stream<Arguments> =
            Stream.of(
                Arguments.of("", ""),
                Arguments.of("(Ä)Öüß Äöü - Üäö 1 (2345)", "ÄÖüßÄöü_Üäö1_2345"),
                Arguments.of("(A)Bcd Efg - Hij 1 (2345)", "ABcdEfg_Hij1_2345"),
                Arguments.of("1 Abc def Ghi (2345)", "1AbcDefGhi_2345"),
                Arguments.of("aBc - Def G (1234)", "aBc_DefG_1234"),
                Arguments.of("aBc 1 - Def G (2345)", "aBc1_DefG_2345"),
                Arguments.of("A-Bcd (1234)", "ABcd_1234"),
                Arguments.of("1½ Abc (2345)", "1_Abc_2345"),
                Arguments.of("12.345 A.B (6789)", "12345AB_6789"),
                Arguments.of("Abc 1 - ... def (2345)", "Abc1_def_2345"),
                Arguments.of("Abc.def (1234)", "AbcDef_1234"),
                Arguments.of("Abc & Def (1234)", "AbcAndDef_1234"),
                Arguments.of("Ab\$c (1234)", "Absc_1234"),
                Arguments.of("1234-56-78 - Abc def ghi", "12345678_AbcDefGhi"),
                Arguments.of("#Abc#def", "AbcDef"),
                Arguments.of("abc.", "abc"),
                Arguments.of("Ab. Cde", "AbCde"),
                Arguments.of("Ab, cde", "AbCde"),
            )
    }
}