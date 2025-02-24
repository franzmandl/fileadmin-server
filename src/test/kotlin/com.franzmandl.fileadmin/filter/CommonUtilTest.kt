package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.stream.Stream

class CommonUtilTest {
    @ParameterizedTest(name = """"{0}"""")
    @MethodSource
    fun testParseDate(string: String, expected: LocalDate?) {
        assertThat(CommonUtil.parseDate(string)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun testParseDate(): Stream<Arguments> =
            Stream.of(
                Arguments.of("", null),
                Arguments.of("1", null),
                Arguments.of("0000", LocalDate.of(0, 1, 1)),
                Arguments.of("1111", LocalDate.of(1111, 1, 1)),
                Arguments.of("1111 ", LocalDate.of(1111, 1, 1)),
                Arguments.of("1111-", null),
                Arguments.of("11111", null),
                Arguments.of("1111a", null),
                Arguments.of("9999-12-24", LocalDate.of(9999, 12, 24)),
                Arguments.of("9999-99-99", LocalDate.of(9999, 12, 31)),
                Arguments.of("9999-02-99", LocalDate.of(9999, 2, 28)),
                Arguments.of("9999-02-99.txt", LocalDate.of(9999, 2, 28)),
                Arguments.of("9999-02-99 - ", LocalDate.of(9999, 2, 28)),
                Arguments.of("9999-02-99a", null),
                Arguments.of("2010-02-05+...", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05 06.07", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05P1", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05R1M", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05R!1M", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05E-1M", LocalDate.of(2010, 2, 5)),
                Arguments.of("2010-02-05 21", LocalDate.of(2010, 2, 5)),
            )
    }
}