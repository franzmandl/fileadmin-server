package com.franzmandl.fileadmin.filter

import com.franzmandl.fileadmin.common.CommonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CommonUtilTests {
    @Test
    fun testParseDate() {
        assertThat(CommonUtil.parseDate("")).isNull()
        assertThat(CommonUtil.parseDate("1")).isNull()
        assertThat(CommonUtil.parseDate("0000")).isEqualTo(LocalDate.of(0, 1, 1))
        assertThat(CommonUtil.parseDate("1111")).isEqualTo(LocalDate.of(1111, 1, 1))
        assertThat(CommonUtil.parseDate("1111 ")).isEqualTo(LocalDate.of(1111, 1, 1))
        assertThat(CommonUtil.parseDate("1111-")).isEqualTo(LocalDate.of(1111, 1, 1))
        assertThat(CommonUtil.parseDate("11111")).isNull()
        assertThat(CommonUtil.parseDate("1111a")).isEqualTo(LocalDate.of(1111, 1, 1))
        assertThat(CommonUtil.parseDate("9999-12-24")).isEqualTo(LocalDate.of(9999, 12, 24))
        assertThat(CommonUtil.parseDate("9999-99-99")).isEqualTo(LocalDate.of(9999, 12, 31))
        assertThat(CommonUtil.parseDate("9999-02-99")).isEqualTo(LocalDate.of(9999, 2, 28))
        assertThat(CommonUtil.parseDate("9999-02-99.txt")).isEqualTo(LocalDate.of(9999, 2, 28))
        assertThat(CommonUtil.parseDate("9999-02-99 - ")).isEqualTo(LocalDate.of(9999, 2, 28))
        assertThat(CommonUtil.parseDate("9999-02-99a")).isEqualTo(LocalDate.of(9999, 2, 28))
        assertThat(CommonUtil.parseDate("2010-02-05+...")).isEqualTo(LocalDate.of(2010, 2, 5))
    }
}