package com.franzmandl.fileadmin.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CommonUtilTest {
    @Test
    fun testGetPartsList() {
        assertThat(CommonUtil.getSequenceOfParts(listOf<String>(), true).toList()).isEqualTo(listOf(listOf<String>()))
        assertThat(CommonUtil.getSequenceOfParts(listOf("a"), true).toList()).isEqualTo(listOf(listOf(), listOf("a")))
        assertThat(CommonUtil.getSequenceOfParts(listOf(" "), true).toList()).isEqualTo(listOf(listOf(), listOf(" ")))
    }

    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testStringToUri(string: String, expected: String) {
        assertThat(CommonUtil.stringToUri(string)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun testStringToUri(): Stream<Arguments> =
            Stream.of(
                Arguments.of("", ""),
                Arguments.of("/", "/"),
                Arguments.of(" ", "%20"),
                Arguments.of("+", "%2B"),
                Arguments.of("’", "%E2%80%99"),
                Arguments.of("｜", "%EF%BD%9C"),
                Arguments.of("ABC", "ABC"),
                Arguments.of("abc", "abc"),
                Arguments.of("123", "123"),
                Arguments.of("%", "%25"),
            )
    }
}