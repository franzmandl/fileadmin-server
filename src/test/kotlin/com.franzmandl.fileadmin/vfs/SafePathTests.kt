package com.franzmandl.fileadmin.vfs

import com.franzmandl.fileadmin.common.HttpException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class SafePathTests {
    @Test
    fun testConstructor() {
        assertThat(SafePath("/a/b/c")).isNotNull
        assertThat(SafePath("/a/.../b/c")).isNotNull
        assertThat(SafePath("/a/b/c/..d")).isNotNull
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/a/b/c/..") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("../a/b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("../a/../b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/../a/../b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/../a/b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/a/../b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("./a/b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("./a/./b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/./a/./b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/./a/b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("/a/./b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("a/b/c") }
        assertThatExceptionOfType(HttpException::class.java).isThrownBy { SafePath("") }
    }

    @Test
    fun testIsRoot() {
        assertThat(SafePath(listOf()).isRoot).isTrue
        assertThat(SafePath(listOf("a")).isRoot).isFalse
        assertThat(SafePath(listOf(" ")).isRoot).isFalse
    }

    @Test
    fun testParent() {
        assertThat(SafePath(listOf()).parent).isNull()
        assertThat(SafePath(listOf("a")).parent).isEqualTo(SafePath(listOf()))
        assertThat(SafePath(listOf("a", "b")).parent).isEqualTo(SafePath(listOf("a")))
    }
}