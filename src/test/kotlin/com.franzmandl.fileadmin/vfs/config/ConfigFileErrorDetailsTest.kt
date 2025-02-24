package com.franzmandl.fileadmin.vfs.config

import com.franzmandl.fileadmin.common.JsonFormat
import com.franzmandl.fileadmin.dto.config.ConfigRoot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

class ConfigFileErrorDetailsTest {
    @Test
    fun test1() {
        val text = """{
    "_type": "FilterVersion1",
    "tags": [
        {"_type": "TagVersion1", "name": "#Abc", "children": [
            {"_type": "TagVersion1", "name": "#Bcd"},
        null]}
        {"_type": "TagVersion1", "name": "#Cde", "children": [
            {"_type": "TagVersion1", "name": "#Def"},
        null]},
    null]
}
"""
        try {
            JsonFormat.decodeFromString<ConfigRoot>(text)
            fail("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).startsWith("Unexpected JSON token at offset 187: ")
            assertThat(ConfigFileErrorDetails.create(e) { text }).isEqualTo(ConfigFileErrorDetails(6, "#Cde"))
        }
    }

    @Test
    fun test2() {
        val text = """{
    "_type": "FilterVersion1",
    "tags": [
        {"_type": "TagVersion1", "name": "#Abc", "children": [
            {"_type": "TagVersion1", "name": "#Bcd"},
        null]},
        {"_type": "TagVersion1", "name": "#Cde", "children": [
            {"_type": "TagVersion1", "name": "#Def"},
        null]}
    null]
}
"""
        try {
            JsonFormat.decodeFromString<ConfigRoot>(text)
            fail("Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).startsWith("Unexpected JSON token at offset 316: ")
            assertThat(ConfigFileErrorDetails.create(e) { text }).isEqualTo(ConfigFileErrorDetails(9, null))
        }
    }
}