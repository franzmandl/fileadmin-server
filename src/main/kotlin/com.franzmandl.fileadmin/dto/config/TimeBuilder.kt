package com.franzmandl.fileadmin.dto.config

import com.franzmandl.fileadmin.common.CommonUtil
import java.time.LocalDate

class TimeBuilder {
    private var year: String? = null
    private var month: String? = null
    private var day: String? = null
    var time: LocalDate? = null

    fun setYear(year: String) {
        this.time = null
        this.year = year
        this.month = null
        this.day = null
    }

    fun setYearMonth(year: String, month: String) {
        this.time = null
        this.year = year
        this.month = month
        this.day = null
    }

    fun setYearMonthDay(year: String, month: String, day: String) {
        this.time = null
        this.year = year
        this.month = month
        this.day = day
    }

    fun build(): LocalDate? =
        if (time != null) {
            time
        } else {
            time = year?.let { CommonUtil.parseDate(it, month, day) }
            time
        }
}