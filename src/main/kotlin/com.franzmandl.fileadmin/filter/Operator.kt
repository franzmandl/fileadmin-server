package com.franzmandl.fileadmin.filter

enum class CompareOperator(val text: String) {
    Equal("=="),
    EqualNot("!="),
    GreaterEqual(">="),
    Greater(">"),
    SmallerEqual("<="),
    Smaller("<"),
    ;

    fun <T> apply(a: Comparable<T>, b: T): Boolean = when (this) {
        Equal -> a == b
        EqualNot -> a != b
        GreaterEqual -> a >= b
        Greater -> a > b
        SmallerEqual -> a <= b
        Smaller -> a < b
    }
}