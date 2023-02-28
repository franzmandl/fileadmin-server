package com.franzmandl.fileadmin.filter

enum class StringOperator {
    Contains,
    EndsWith,
    StartsWith,
    ;

    fun apply(a: String, b: String): Boolean = when (this) {
        Contains -> a.uppercase().contains(b.uppercase())
        EndsWith -> a.uppercase().endsWith(b.uppercase())
        StartsWith -> a.uppercase().startsWith(b.uppercase())
    }
}

enum class CompareOperator {
    Equal,
    EqualNot,
    GreaterEqual,
    Greater,
    SmallerEqual,
    Smaller,
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